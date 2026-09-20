package com.alsaril.bf.generator

import com.alsaril.bf.Command.*
import com.alsaril.bf.CommandInstruction
import com.alsaril.bf.Instruction
import com.alsaril.bf.Loop
import com.alsaril.codegen.classfile.ClassFileBuilder
import com.alsaril.codegen.classfile.Fragment
import com.alsaril.codegen.classfile.MethodAccessFlag.*
import com.alsaril.codegen.classfile.code.*
import com.alsaril.codegen.classfile.PrimitiveType.BYTE
import com.alsaril.codegen.classfile.PrimitiveType.INT
import com.alsaril.codegen.classfile.join
import kotlin.math.min

object RunGenerator {

    private const val methodLengthLimit = 8000 // hotspot threshold, however can be as big as 65535

    private const val inIndex = 0
    private const val outIndex = 1
    private const val memsizeIndex = 2
    private const val arrayIndex = 3
    private const val stateIndex = 4 // pointer, cycles
    private const val readIndex = 5

    fun ClassFileBuilder.generateRun(instructions: List<Instruction>) = apply {
        val generation = Generation(bodyLengthLimit(), loopOverhead())
        val body = materializeNonrecursive(instructions, generation)
        val (name, descriptor) = defineMethod(body, generation)
        method("run", "(Ljava/io/InputStream;Ljava/io/OutputStream;II)V", PUBLIC, FINAL) {
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

            val ready = aload(1)
            aload(2)
            iload(3)
            aload(4)
            aload(5)

            frameFull(
                ready,
                listOf(
                    objInfo(self()),
                    objInfo("java/io/InputStream"),
                    objInfo("java/io/OutputStream"),
                    IntInfo,
                    objInfo("[B"),
                    objInfo("[I")
                ), emptyList()
            )

            val guarded = invokestatic(smethod(self(), name, descriptor))
            aconst_null()

            val caught = aload(2)
            `catch`(guarded, to = caught, handler = caught, type = null)
            frameStack(caught, objInfo("java/lang/Throwable"))
            invokevirtual(method(clazz("java/io/OutputStream"), "flush", "()V"))

            dup()
            val exit = ifnull()
            athrow()

            val done = `return`()
            link(exit, done)
            frameStack(done, objInfo("java/lang/Throwable"))
        }
    }

    private fun ClassFileBuilder.defineMethod(body: Fragment, generation: Generation): Pair<String, String> {
        val name = "f${generation.nextIndex()}"
        val descriptor = "(Ljava/io/InputStream;Ljava/io/OutputStream;I[B[I)V"
        method(name, descriptor, wrapMethodBody(body), PRIVATE, STATIC, FINAL)
        return name to descriptor
    }

    private fun ClassFileBuilder.materializeNonrecursive(
        instructions: List<Instruction>,
        generation: Generation
    ): Fragment {
        val stack = mutableListOf<Pair<List<Instruction>, MutableList<Fragment>>>()
        stack.add(instructions to mutableListOf())
        while (true) {
            val (input, output) = stack.last()
            if (input.size == output.size) {
                if (stack.size == 1) {
                    val fragment = combine(output, generation, loop = false)
                    return fragment
                }
                val fragment = combine(output, generation, loop = true)
                stack.removeLast()
                stack.last().second.add(fragment)
                continue
            }

            when (val instruction = input[output.size]) {
                is CommandInstruction -> output.add(
                    when (instruction.command) {
                        LEFT -> emitMove(instruction.times, false)
                        RIGHT -> emitMove(instruction.times, true)
                        INC -> emitAdd(instruction.times, true)
                        DEC -> emitAdd(instruction.times, false)
                        IN -> emitRead()
                        OUT -> emitWrite()
                    }
                )

                is Loop -> stack.add(instruction.instructions to mutableListOf())
            }
        }
    }

    private fun tryInline(fragments: List<Fragment>, budget: Int): Fragment? {
        val chunk = collect(fragments, 0, budget)
        if (chunk.next != null) return null
        return chunk.fragments.join()
    }

    private fun ClassFileBuilder.pack(
        fragments: List<Fragment>,
        generation: Generation,
        budget: Int
    ): Fragment = tryInline(fragments, budget) ?: run {
        var start: Int? = 0
        val calls = mutableListOf<Fragment>()

        while (start != null) {
            val chunk = collect(fragments, start, generation.bodyLengthLimit, allowSingleFragmentSpill = true)
            calls.add(emitCall(defineMethod(chunk.fragments.join(), generation)))
            start = chunk.next
        }

        pack(calls, generation, budget) // recursive, but the depth is log(program length)
    }

    private fun ClassFileBuilder.combine(
        fragments: List<Fragment>,
        generation: Generation,
        loop: Boolean
    ): Fragment {
        val budget = generation.bodyLengthLimit - if (loop) generation.loopOverhead else 0
        val body = pack(fragments, generation, budget)

        return if (loop) emitLoop(body) else body
    }

    private fun CodeBuilder.guard() {
        dup()
        iload(memsizeIndex)
        invokestatic(smethod(self(), "guard", "(II)V"))
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

        val ok_ = aload(arrayIndex)
        link(ok, ok_)
        frameSame(ok_)

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

    private fun ClassFileBuilder.emitLoop(body: Fragment) = emitFragment {
        val exit = goto() // jump over the loop body, to the test below

        // cycles check
        val head = aload(stateIndex)
        frameSame(head)
        iconst(1)
        dup2()
        iaload()
        iconst(1)
        isub()
        dup_x2()
        iastore()
        val safe = ifge()
        raise("Cycles overflow")

        val start = fragment(body)
        link(safe, start)
        frameSame(start)

        val test = aload(arrayIndex)
        link(exit, test)
        frameSame(test)
        aload(stateIndex)
        iconst(0)
        iaload()
        guard()
        baload()
        ifne(head)
    }

    private fun ClassFileBuilder.emitCall(target: Pair<String, String>) = emitFragment {
        val (name, descriptor) = target
        aload(inIndex)
        aload(outIndex)
        iload(memsizeIndex)
        aload(arrayIndex)
        aload(stateIndex)
        invokestatic(smethod(self(), name, descriptor))
    }

    private fun ClassFileBuilder.wrapMethodBody(body: Fragment) = emitFragment {
        iconst(0)
        istore(readIndex)
        val start = fragment(body)
        frameAppend(start, IntInfo)
        `return`()
    }

    private val EMPTY = Fragment(emptyList(), emptyMap(), emptyList(), emptyList(), size = 0)

    private fun ClassFileBuilder.bodyLengthLimit() = methodLengthLimit - wrapMethodBody(EMPTY).size

    private fun ClassFileBuilder.loopOverhead() = emitLoop(EMPTY).size
}
