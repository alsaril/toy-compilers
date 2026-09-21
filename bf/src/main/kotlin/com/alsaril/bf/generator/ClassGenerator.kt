package com.alsaril.bf.generator

import com.alsaril.bf.Instruction
import com.alsaril.bf.generator.RunGenerator.generateRun
import com.alsaril.codegen.classfile.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.MethodAccessFlag.*
import com.alsaril.codegen.classfile.code.*


object ClassGenerator {

    fun generate(instructions: List<Instruction>) = classFile("Impl", parent = "java/lang/Object")
        .iface("com/alsaril/bf/Program")
        .method("<init>", "()V", PUBLIC) {
            aload(0)
            invokespecial(method(parent(), "<init>", "()V"))
            `return`()
        }
        .method("guard", "(II)V", PRIVATE, FINAL, STATIC) {
            iload(0)
            iconst(0)
            val j1 = if_icmplt()

            iload(0)
            iload(1)
            val j2 = if_icmpge()

            `return`()

            val handler = raise("Buffer overflow")
            link(j1, handler); link(j2, handler)
            frameSame(handler)
        }
        .generateRun(instructions)
        .build()
}
