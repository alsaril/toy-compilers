package com.alsaril.bf

import com.alsaril.bf.ir.*
import com.alsaril.codegen.classfile.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.CodeBuilder.ArrayType.BYTE
import com.alsaril.codegen.classfile.CodeBuilder.IntInfo
import com.alsaril.codegen.classfile.CodeBuilder.ObjInfo
import com.alsaril.codegen.classfile.MethodAccessFlag.*


object CodeGenerator {

    private val maxMemsize = 30000

    fun generate(instructions: List<IrInstruction>): Pair<String, ByteArray> {
        return classFile("Impl", parent = "java/lang/Object")
            .iface("java/lang/Runnable")
            .method("<init>", "()V", maxStack = 1, maxLocals = 1, PUBLIC) {
                aload(0)
                invokespecial(method(parent(), "<init>", "()V"))
                `return`()
            }
            .method("guard", "(I)V", maxStack = 2, maxLocals = 1, PRIVATE, FINAL, STATIC) {
                iload(0)
                iconst(0)
                val j1 = if_icmplt()

                iload(0)
                iconst(maxMemsize)
                val j2 = if_icmpge()

                `return`()

                val handler = loc()
                j1(handler); j2(handler)
                frameSame()

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

                frameAppend(ObjInfo("[B"), IntInfo)

                fun guard() {
                    iload(pointerIndex)
                    invokestatic(guard)
                }

                fun apply(times: Int, inc: Boolean) { // maxStack 4
                    guard()
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
                                guard()
                                getstatic(outField)
                                aload(arrayIndex)
                                iload(pointerIndex)
                                baload()
                                invokevirtual(printChar)
                            }

                            Command.IN -> { // maxStack 3
                                guard()
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
                            frameSame()
                            loopStartInfo[i] = loopBody to destCallback
                        }

                        is LoopEnd -> {
                            val (location, callback) = loopStartInfo[it.startIndex]!!

                            val thisInstruction = loc()
                            frameSame()
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