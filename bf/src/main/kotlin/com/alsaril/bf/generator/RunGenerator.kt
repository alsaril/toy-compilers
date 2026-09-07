package com.alsaril.bf.generator

import com.alsaril.bf.Command.*
import com.alsaril.bf.CommandInstruction
import com.alsaril.bf.Instruction
import com.alsaril.bf.Loop
import com.alsaril.codegen.classfile.ClassFileBuilder
import com.alsaril.codegen.classfile.Fragment
import com.alsaril.codegen.classfile.MethodAccessFlag.*
import com.alsaril.codegen.classfile.attributes.*
import com.alsaril.codegen.classfile.code.*
import com.alsaril.codegen.classfile.code.ArrayType.BYTE
import com.alsaril.codegen.classfile.code.ArrayType.INT
import kotlin.math.min

object RunGenerator {

    private val methodLengthLimit = 8000 // hotspot threshold, however can be as big as 65535

    private val inIndex = 0
    private val outIndex = 1
    private val memsizeIndex = 2
    private val arrayIndex = 3
    private val stateIndex = 4 // pointer, cycles
    private val readIndex = 5

    fun ClassFileBuilder.generateRun(instructions: List<Instruction>) = apply {
        val counter = MutableInt() // TODO fix reverse order
        val body = materialize(instructions, methodLengthLimit, counter)
        val (name, descriptor) = defineMethod(body, counter)
        method("run", "(Ljava/io/InputStream;Ljava/io/OutputStream;II)V", maxStack = 5, maxLocals = 6, PUBLIC, FINAL) {
            // input: in, out, size, cycles
            iconst(2)
            newarray(INT)
            dup()
            astore(5)
            iconst(1)
            iload(4) // cycles arg # stack 3
            iastore()

            iload(3) // memsize arg
            newarray(BYTE)
            astore(4)

            aload(1)
            aload(2)
            iload(3)
            aload(4)
            aload(5)
            invokestatic(method(self(), name, descriptor))

            aload(2)
            invokevirtual(method(clazz("java/io/OutputStream"), "flush", "()V"))
            `return`()
        }
    }

    private fun ClassFileBuilder.defineMethod(fragment: Fragment, counter: MutableInt): Pair<String, String> {
        val name = "f${counter.inc()}"
        val descriptor = "(Ljava/io/InputStream;Ljava/io/OutputStream;I[B[I)V"
        val prefix = emitFragment {
            iconst(0) // 1 byte
            istore(readIndex) // 3 bytes
            frameAppend(IntInfo)
        }
        val postfix = emitFragment { `return`() }
        val body = joinFragments(listOf(prefix, fragment, postfix))
        method(name, descriptor, body, 5, 6, PRIVATE, STATIC, FINAL)
        return name to descriptor
    }

    private fun ClassFileBuilder.emitCall(method: Pair<String, String>) = emitFragment {
        val (name, descriptor) = method
        aload(0)
        aload(1)
        iload(2)
        aload(3)
        aload(4)
        invokestatic(method(self(), name, descriptor))
    }

    private data class MutableInt(var value: Int = 0) {
        fun inc() = value++
    }

    private data class Chunk(val fragments: List<Fragment>, val size: Int, val next: Int?)

    private fun collect(fragments: List<Fragment>, start: Int, maxsize: Int): Chunk {
        val result = mutableListOf<Fragment>()
        var size = 0
        fragments.asSequence().drop(start).forEachIndexed { index, fragment ->
            if (size + fragment.size > maxsize) return Chunk(result, size, start + index)
            result.add(fragment)
            size += fragments.size
        }
        return Chunk(result, size, null)
    }

    private fun joinFragments(fragments: List<Fragment>): Fragment {
        if (fragments.size == 1) return fragments.first()
        val result = mutableListOf<ByteArray>()
        val globalFrames = mutableListOf<StackMapFrame>()
        var globalBase = 0
        var globalSize = 0

        fragments.forEach { (code, frames, size) ->
            result.addAll(code)
            if (frames.isNotEmpty()) {
                val frame = frames.first()
                val patchedOffset = frame.offsetDelta + globalSize - globalBase
                if (patchedOffset != -1) {
                    val patched = when (frame) {
                        is SameFrame, is SameFrameExtended -> sameFrame(patchedOffset)
                        is AppendFrame -> frame.copy(offsetDelta = patchedOffset)
                        is FullFrame -> frame.copy(offsetDelta = patchedOffset)
                    }
                    globalFrames.add(patched)
                    globalBase += patched.offsetDelta + 1
                }
            }
            frames.asSequence().drop(1).forEach {
                globalFrames.add(it)
                globalBase += it.offsetDelta + 1
            }
            globalSize += size
        }

        return Fragment(result, globalFrames, globalSize)
    }

    // join into one fragment
    private fun tryInline(fragments: List<Fragment>, budget: Int): Fragment? {
        val chunk = collect(fragments, 0, budget)
        if (chunk.next != null) return null
        return joinFragments(chunk.fragments)
    }

    private fun ClassFileBuilder.materialize(
        instructions: List<Instruction>,
        budget: Int,
        counter: MutableInt
    ): Fragment {
        val fragments: List<Fragment> = instructions.map { materialize(it, counter) }

        tryInline(fragments, budget)?.let { return it }

        // will outline, actual sink
        var start: Int? = 0
        var size = 0
        val result = mutableListOf<ByteArray>()
        while (start != null) { // TODO inline last part if within the budget
            val chunk = collect(fragments, start, methodLengthLimit - 4) // TODO extract this from define method
            val ref = defineMethod(joinFragments(chunk.fragments), counter)
            val (call, frames, s) = emitCall(ref)
            require(frames.isEmpty())
            result.addAll(call)
            size += s
            start = chunk.next
        }

        return Fragment(result, emptyList(), size)
    }

    private fun ClassFileBuilder.materialize(instruction: Instruction, counter: MutableInt): Fragment {
        if (instruction is Loop) {
            // warning: short jumps only!
            val (prefix, toTail, start) = emitPrefix()
            val (postfix, toHead, end) = emitPostfix()
            val overhead = prefix.size + postfix.size
            val body = materialize(instruction.instructions, methodLengthLimit - overhead, counter)
            toTail(prefix.size + body.size + end)
            toHead(start - prefix.size - body.size)
            val fragments = listOf(prefix, body, postfix)
            return joinFragments(fragments)
        } else if (instruction is CommandInstruction) {
            val codeWithFrames = when (instruction.command) {
                LEFT -> emitMove(instruction.times, false)
                RIGHT -> emitMove(instruction.times, true)
                INC -> emitAdd(instruction.times, true)
                DEC -> emitAdd(instruction.times, false)
                IN -> emitRead()
                OUT -> emitWrite()
            }
            return codeWithFrames
        }
        throw IllegalArgumentException()
    }

    private fun CodeBuilder.guard() {
        dup()
        iload(memsizeIndex)
        invokestatic(method(self(), "guard", "(II)V"))
    }

    private fun ClassFileBuilder.emitMove(times: Int, dir: Boolean) = emitFragment {
        aload(stateIndex)
        iconst(0)
        dup2()
        iaload()

        var t = times
        while (t > 0) {
            val d = min(t, Short.MAX_VALUE.toInt())
            iconst(d)
            if (dir) iadd() else isub()
            t -= d
        }

        iastore()
    }

    private fun ClassFileBuilder.emitAdd(times: Int, inc: Boolean) = emitFragment {
        aload(arrayIndex)
        aload(stateIndex)
        iconst(0)
        iaload()
        guard()
        dup2()
        baload()
        // mod 256
        iconst(times and 0xff)
        if (inc) iadd() else isub()
        bastore()
    }

    private fun ClassFileBuilder.emitRead() = emitFragment {
        aload(inIndex)
        invokevirtual(method(clazz("java/io/InputStream"), "read", "()I"))
        istore(readIndex)

        // eof fix -1 -> 0
        iload(readIndex)
        val ok = ifge()
        iconst(0)
        istore(readIndex)

        ok(loc())
        frameSame()

        aload(arrayIndex)
        aload(stateIndex)
        iconst(0)
        iaload()
        guard()
        iload(readIndex)
        bastore()
    }

    private fun ClassFileBuilder.emitWrite() = emitFragment {
        aload(outIndex)
        aload(arrayIndex)
        aload(stateIndex)
        iconst(0)
        iaload()
        guard()
        baload()
        invokevirtual(method(clazz("java/io/OutputStream"), "write", "(I)V"))
    }

    private data class LoopBoundary(
        val fragment: Fragment,
        val exit: (Int) -> Unit,
        val entry: Int
    )

    private fun ClassFileBuilder.emitPrefix(): LoopBoundary {
        val exit: (Int) -> Unit
        val entry: Int
        val fragment = emitFragment {
            exit = goto() // jump over the loop body
            entry = loc()
            frameSame()

            // cycles check
            aload(stateIndex)
            iconst(1)
            dup2()
            iaload()
            iconst(1)
            isub()
            dup_x2()
            iastore()
            val safe = ifge()
            raise("Cycles overflow")

            safe(loc())
            frameSame()
        }

        return LoopBoundary(fragment, exit, entry)
    }

    private fun ClassFileBuilder.emitPostfix(): LoopBoundary {
        val exit: (Int) -> Unit
        val entry: Int
        val fragment = emitFragment {
            entry = loc()
            frameSame()
            aload(arrayIndex)
            aload(stateIndex)
            iconst(0)
            iaload()
            guard()
            baload()
            exit = ifne()
        }
        return LoopBoundary(fragment, exit, entry)
    }
}