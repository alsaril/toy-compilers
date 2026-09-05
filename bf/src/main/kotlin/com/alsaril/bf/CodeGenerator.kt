package com.alsaril.bf

import com.alsaril.bf.ir.*
import com.alsaril.codegen.classfile.code.*
import com.alsaril.codegen.classfile.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.code.ArrayType.BYTE
import com.alsaril.codegen.classfile.MethodAccessFlag.*


object CodeGenerator {

    private val defaulMemsize = 30000

    fun generate(instructions: List<IrInstruction>): Pair<String, ByteArray> {
        fun CodeBuilder.raise(message: String) {
            val exceptionClass = clazz("java/lang/IllegalStateException")
            new(exceptionClass)
            dup()
            ldc(string(message))
            invokespecial(method(exceptionClass, "<init>", "(Ljava/lang/String;)V"))
            athrow()
        }

        return classFile("Impl", parent = "java/lang/Object")
            .iface("com/alsaril/bf/ExtendedRunnable")
            .method("<init>", "()V", maxStack = 1, maxLocals = 1, PUBLIC) {
                aload(0)
                invokespecial(method(parent(), "<init>", "()V"))
                `return`()
            }
            .method("guard", "(II)V", maxStack = 3, maxLocals = 2, PRIVATE, FINAL, STATIC) {
                iload(0)
                iconst(0)
                val j1 = if_icmplt()

                iload(0)
                iload(1)
                val j2 = if_icmpge()

                `return`()

                val handler = loc()
                j1(handler); j2(handler)
                frameSame()

                raise("Buffer overflow")
            }
            .method("run", "()V", maxStack = 5, maxLocals = 1, PUBLIC, FINAL) {
                aload(0)
                getstatic(field(clazz("java/lang/System"), "in", "Ljava/io/InputStream;"))
                getstatic(field(clazz("java/lang/System"), "out", "Ljava/io/PrintStream;"))
                ldc(int(defaulMemsize))
                ldc(int(Int.MAX_VALUE))
                invokespecial(method(self(), "run", "(Ljava/io/InputStream;Ljava/io/OutputStream;II)V"))
                `return`()
            }
            .method("run", "(Ljava/io/InputStream;Ljava/io/OutputStream;II)V", maxStack = 4, maxLocals = 7, PUBLIC, FINAL) {
                val loopStartInfo = mutableMapOf<Int, Pair<Int, (Int) -> Unit>>()

                val inIndex = 1
                val outIndex = 2
                val memsizeIndex = 3
                val cyclesIndex = 4
                val arrayIndex = 5
                val pointerIndex = 6

                iload(memsizeIndex)
                newarray(BYTE)
                astore(arrayIndex)

                iconst(0)
                istore(pointerIndex)

                frameAppend(ObjInfo("[B"), IntInfo)

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
                                aload(outIndex)
                                aload(arrayIndex)
                                iload(pointerIndex)
                                baload()
                                invokevirtual(method(clazz("java/io/OutputStream"), "write", "(I)V"))
                            }

                            Command.IN -> { // maxStack 3
                                guard()
                                aload(arrayIndex)
                                iload(pointerIndex)
                                aload(inIndex)
                                invokevirtual(method(clazz("java/io/InputStream"), "read", "()I"))
                                bastore()
                            }
                        }

                        is LoopBegin -> {
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

                // flush output
                aload(outIndex)
                invokevirtual(method(clazz("java/io/OutputStream"), "flush", "()V"))
                `return`()
            }
            .build()
    }
}