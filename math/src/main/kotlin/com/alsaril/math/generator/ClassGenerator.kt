package com.alsaril.math.generator

import com.alsaril.codegen.code.ClassFileBuilder
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.MethodAccessFlag.FINAL
import com.alsaril.codegen.classfile.MethodAccessFlag.PRIVATE
import com.alsaril.codegen.classfile.MethodAccessFlag.PUBLIC
import com.alsaril.codegen.classfile.MethodAccessFlag.STATIC
import com.alsaril.codegen.code.*
import com.alsaril.codegen.instruction.*
import com.alsaril.math.Node

object ClassGenerator {

    fun generate(ast: Node) = classFile("Impl", parent = "java/lang/Object")
        .iface("com/alsaril/math/Program")
        .method("<init>", "()V", PUBLIC) {
            +aload(0)
            +invokespecial(method(parent(), "<init>", "()V"))
            +`return`
        }
        .emitGetFloat()
        .generateEval(ast)
        .build()

    private fun ClassFileBuilder.emitGetFloat() =
        method("getFloat", "(Ljava/util/Map;Ljava/lang/String;)F", PRIVATE, STATIC, FINAL) {
            +aload(0)
            +aload(1)
            +invokeinterface(imethod(clazz("java/util/Map"), "get", "(Ljava/lang/Object;)Ljava/lang/Object;"))
            +dup
            +instanceof(clazz("java/lang/Float"))
            val err = +ifeq

            +checkcast(clazz("java/lang/Float"))
            +invokevirtual(method(clazz("java/lang/Float"), "floatValue", "()F"))
            +freturn

            val dest = +new(clazz("java/util/NoSuchElementException"))
            frameStack(dest, objInfo("java/lang/Object"))
            link(err, dest)

            +dup
            +aload(1)
            +invokespecial(method(clazz("java/util/NoSuchElementException"), "<init>", "(Ljava/lang/String;)V"))
            +athrow
        }
}
