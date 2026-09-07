package com.alsaril.bf.generator

import com.alsaril.bf.Command.*
import com.alsaril.bf.CommandInstruction
import com.alsaril.bf.Instruction
import com.alsaril.bf.Loop
import com.alsaril.codegen.classfile.ClassFileBuilder
import com.alsaril.codegen.classfile.Fragment
import com.alsaril.codegen.classfile.MethodAccessFlag.*
import com.alsaril.codegen.classfile.code.*
import com.alsaril.codegen.classfile.code.ArrayType.BYTE
import com.alsaril.codegen.classfile.code.ArrayType.INT
import com.alsaril.codegen.classfile.join
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
        val counter = MutableInt()
        val body = materialize(instructions, bodyLengthLimit(), counter)
        val (name, descriptor) = defineMethod(body, counter)
        method("run", "(Ljava/io/InputStream;Ljava/io/OutputStream;II)V", maxStack = 5, maxLocals = 6, PUBLIC, FINAL) {
            // input: in, out, size, cycles
            iconst(2)
            newarray(INT)
            dup()
            astore(5)
            iconst(1)
            iload(4) // cycles arg
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
        val prefix = emitMethodPrefix()
        val postfix = emitMethodPostfix()
        val body = listOf(prefix, fragment, postfix).join()
        method(name, descriptor, body, 5, 6, PRIVATE, STATIC, FINAL)
        return name to descriptor
    }

    // join into one fragment
    private fun tryInline(fragments: List<Fragment>, budget: Int): Fragment? {
        val chunk = collect(fragments, 0, budget)
        if (chunk.next != null) return null
        return chunk.fragments.join()
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
        val chunkBudget = bodyLengthLimit()
        while (start != null) {
            val chunk = collect(fragments, start, chunkBudget, allowSingleFragmentSpill = true)
            val ref = defineMethod(chunk.fragments.join(), counter)
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
            val body = materialize(instruction.instructions, bodyLengthLimit() - overhead, counter)
            toTail(prefix.size + body.size + end)
            toHead(start - prefix.size - body.size)
            val fragments = listOf(prefix, body, postfix)
            return fragments.join()
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

    private fun ClassFileBuilder.emitCall(method: Pair<String, String>) = emitFragment {
        val (name, descriptor) = method
        aload(0)
        aload(1)
        iload(2)
        aload(3)
        aload(4)
        invokestatic(method(self(), name, descriptor))
    }

    private fun ClassFileBuilder.emitMethodPrefix() = emitFragment {
        iconst(0)
        istore(readIndex)
        frameAppend(IntInfo)
    }

    private fun ClassFileBuilder.emitMethodPostfix() = emitFragment { `return`() }

    private fun ClassFileBuilder.bodyLengthLimit() =
        methodLengthLimit - emitMethodPrefix().size - emitMethodPostfix().size
}