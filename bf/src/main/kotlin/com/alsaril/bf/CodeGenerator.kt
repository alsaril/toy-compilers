package com.alsaril.bf

import com.alsaril.bf.ir.IrInstruction
import com.alsaril.codegen.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.MethodModifier.*
import com.sun.tools.javac.jvm.ByteCodes.aload
import com.sun.tools.javac.jvm.ByteCodes.bastore
import com.sun.tools.javac.jvm.ByteCodes.iload


object CodeGenerator {

    private val maxMemsize = 30000

    fun generate(instructions: List<IrInstruction>): Pair<String, ByteArray> {
        return classFile("Impl", parent = "java/lang/Object")
            .iface("java/lang/Runnable")
            .method("<init>", "()V", maxStack = 1, maxLocals = 0, PUBLIC) {
                val superCall = method(parent(), "<init>", "()V")

                aload(0)
                invokespecial(superCall)
                `return`()
            }
            .method("guard", "(I)V", maxStack = 2, maxLocals = 1, PRIVATE, FINAL) {
                iload(1)
                iconst(0)
                val j1 = if_icmplt()

                iload(1)
                iconst(maxMemsize)
                val j2 = if_icmpge()

                `return`()

                val handler = loc()
                j1(handler); j2(handler)
                frame(same())

                construct(clazz("java/lang/ArrayIndexOutOfBoundsException"), "<init>", "()V")
                athrow()
            }
            .method("run", "()V", maxStack = 4, maxLocals = 3, PUBLIC, FINAL) {
                val loopStartInfo = mutableMapOf<Int, Pair<Int, (Int) -> Unit>>()

                val guard = method(self(), "guard", "(I)V")
                val outField = field(clazz("java/lang/System"), "out", "Ljava/io/PrintStream;")
                val printChar = method(clazz("java/io/PrintStream"), "print", "(C)V")
                val inField = field(clazz("java/lang/System"), "in", "Ljava/io/InputStream;")
                val readChar = method(clazz("java/io/InputStream"), "read", "()I")

                // 0 -- this
                // 1 -- array of 30000 elements
                // 2 -- data pointer

                val arrayIndex = 1
                val pointerIndex = 2

                iconst(maxMemsize)
                newarray(BYTE)
                astore(arrayIndex)

                iconst(0)
                istore(pointerIndex)

                frame(append(objInfo("[B"), intInfo))

                fun apply(times: Int, inc: Boolean) { // maxStack 4
                    aload(0)
                    iload(pointerIndex)
                    invokespecial(guard)

                    aload(arrayIndex)
                    iload(pointerIndex)
                    dup2()
                    baload()
                    iconst(times)
                    if (inc) iadd() else isub()
                    bastore()
                }

                instructions.forEachIndexed { i, it ->
                    when (it) {
                        is CommandInstruction -> when (it.command) {
                            Command.LEFT -> iinc(pointerIndex, -it.times)
                            Command.RIGHT -> iinc(pointerIndex, +it.times)
                            Command.INC -> apply(it.times, inc = true)
                            Command.DEC -> apply(it.times, inc = false)
                            Command.OUT -> { // maxStack 3
                                getstatic(outField)
                                aload(arrayIndex)
                                iload(pointerIndex)
                                baload()
                                invokevirtual(printChar)
                            }

                            Command.IN -> { // maxStack 3
                                aload(arrayIndex)
                                iload(pointerIndex)
                                getstatic(inField)
                                invokevirtual(readChar)
                                bastore()
                            }
                        }

                        is LoopBegin -> {
                            val destCallback = goto() // jump over the loop body
                            val loopBody = loc()
                            frame(same())
                            loopStartInfo[i] = loopBody to destCallback
                        }

                        is LoopEnd -> {
                            val (location, callback) = loopStartInfo[it.startIndex]!!

                            val thisInstruction = loc()
                            frame(same())
                            aload(arrayIndex)
                            iload(pointerIndex)
                            baload()
                            ifne(location)
                            callback(thisInstruction)
                        }
                    }
                }

                `return`()
            }
            .build()
    }
}