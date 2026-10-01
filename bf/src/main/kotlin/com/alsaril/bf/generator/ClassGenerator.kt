package com.alsaril.bf.generator

import com.alsaril.bf.Instruction
import com.alsaril.bf.generator.RunGenerator.generateRun
import com.alsaril.codegen.classfile.AccessFlag.*
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.code.invokespecial
import com.alsaril.codegen.code.parent
import com.alsaril.codegen.instruction.*
import java.lang.invoke.MethodHandles


object ClassGenerator {
    internal val lookup: MethodHandles.Lookup = MethodHandles.lookup()

    fun generate(instructions: List<Instruction>) = classFile("com/alsaril/bf/generator/Impl", parent = "java/lang/Object")
        .iface("com/alsaril/bf/Program")
        .method("<init>", "()V", PUBLIC) {
            +aload(0)
            invokespecial(parent(), "<init>", "()V")
            +`return`
        }
        .method("guard", "(II)V", PRIVATE, FINAL, STATIC) {
            +iload(0)
            +iconst(0)
            val j1 = +if_icmplt

            +iload(0)
            +iload(1)
            val j2 = +if_icmpge

            +`return`

            val handler = raise("Buffer overflow")
            link(j1, handler); link(j2, handler)
        }
        .generateRun(instructions)
        .build()
}
