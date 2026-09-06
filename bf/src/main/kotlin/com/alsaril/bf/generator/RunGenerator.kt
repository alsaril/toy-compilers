package com.alsaril.bf.generator

import com.alsaril.bf.Command.*
import com.alsaril.bf.CommandInstruction
import com.alsaril.bf.Instruction
import com.alsaril.bf.Loop
import com.alsaril.codegen.classfile.ClassFileBuilder
import com.alsaril.codegen.classfile.MethodAccessFlag.FINAL
import com.alsaril.codegen.classfile.MethodAccessFlag.PUBLIC
import com.alsaril.codegen.classfile.code.*
import com.alsaril.codegen.classfile.code.ArrayType.BYTE
import kotlin.math.max
import kotlin.math.min

object RunGenerator {
    fun ClassFileBuilder.generateRun(ins: List<Instruction>) = apply {
        method("run", "(Ljava/io/InputStream;Ljava/io/OutputStream;II)V", maxStack = 4, maxLocals = 8, PUBLIC, FINAL) {
            val inIndex = 1
            val outIndex = 2
            val memsizeIndex = 3
            val cyclesIndex = 4
            val arrayIndex = 5
            val pointerIndex = 6
            val readIndex = 7

            iload(memsizeIndex)
            newarray(BYTE)
            astore(arrayIndex)

            iconst(0)
            istore(pointerIndex)

            iconst(0)
            istore(readIndex)

            frameAppend(ObjInfo("[B"), IntInfo, IntInfo)

            fun guard() {
                iload(pointerIndex)
                iload(memsizeIndex)
                invokestatic(method(self(), "guard", "(II)V"))
            }

            fun apply(times: Int, inc: Boolean) { // maxStack 4
                guard()
                aload(arrayIndex)
                iload(pointerIndex)
                dup2()
                baload()
                // mod 256
                iconst(times and 0xff)
                if (inc) iadd() else isub()
                bastore()
            }

            fun inc(times: Int) {
                if (times in Short.MIN_VALUE..Short.MAX_VALUE) {
                    iinc(pointerIndex, times)
                    return
                }
                var t = times
                while (t > 0) {
                    val d = min(t, Short.MAX_VALUE.toInt())
                    iinc(pointerIndex, d)
                    t -= d
                }
                while (t < 0) {
                    val d = max(t, Short.MIN_VALUE.toInt())
                    iinc(pointerIndex, d)
                    t -= d
                }
            }

            fun compileList(instructions: List<Instruction>) {
                instructions.forEachIndexed { i, it ->
                    when (it) {
                        is CommandInstruction -> when (it.command) {
                            LEFT -> inc(-it.times)
                            RIGHT -> inc(it.times)
                            INC -> apply(it.times, inc = true)
                            DEC -> apply(it.times, inc = false)
                            OUT -> { // maxStack 3
                                guard()
                                aload(outIndex)
                                aload(arrayIndex)
                                iload(pointerIndex)
                                baload()
                                invokevirtual(method(clazz("java/io/OutputStream"), "write", "(I)V"))
                            }

                            IN -> { // maxStack 3
                                guard()
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
                                iload(pointerIndex)
                                iload(readIndex)
                                bastore()
                            }
                        }

                        is Loop -> {
                            val destCallback = goto() // jump over the loop body
                            val loopBody = loc()
                            frameSame()

                            // cycles check
                            iinc(cyclesIndex, -1)
                            iload(cyclesIndex)
                            val safe = ifge()
                            raise("Cycles overflow")

                            safe(loc())
                            frameSame()

                            compileList(it.instructions)

                            val thisInstruction = loc()
                            frameSame()
                            aload(arrayIndex)
                            iload(pointerIndex)
                            baload()
                            ifne(loopBody)
                            destCallback(thisInstruction)
                        }
                    }
                }
            }

            compileList(ins)

            // flush output
            aload(outIndex)
            invokevirtual(method(clazz("java/io/OutputStream"), "flush", "()V"))
            `return`()
        }
    }
}