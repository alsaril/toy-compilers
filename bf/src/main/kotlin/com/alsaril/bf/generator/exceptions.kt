package com.alsaril.bf.generator

import com.alsaril.codegen.classfile.code.*

fun CodeBuilder.raise(message: String) {
    val exceptionClass = clazz("java/lang/IllegalStateException")
    new(exceptionClass)
    dup()
    ldc(string(message))
    invokespecial(method(exceptionClass, "<init>", "(Ljava/lang/String;)V"))
    athrow()
}