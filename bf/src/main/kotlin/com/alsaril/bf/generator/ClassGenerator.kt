package com.alsaril.bf.generator

import com.alsaril.bf.generator.RunGenerator.generateRun
import com.alsaril.bf.Instruction
import com.alsaril.codegen.classfile.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.MethodAccessFlag.*
import com.alsaril.codegen.classfile.code.*


object ClassGenerator {

    fun generate(instructions: List<Instruction>): Pair<String, ByteArray> {
        return classFile("Impl", parent = "java/lang/Object")
            .iface("com/alsaril/bf/Program")
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
            .generateRun(instructions)
            .build()
    }
}
