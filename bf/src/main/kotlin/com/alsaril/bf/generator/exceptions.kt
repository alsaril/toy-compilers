package com.alsaril.bf.generator

import com.alsaril.codegen.classfile.code.*
import com.alsaril.codegen.classfile.code.instruction.*

fun CodeBuilder.raise(message: String): Label {
    val exceptionClass = clazz("java/lang/IllegalStateException")
    val start = +new(exceptionClass)
    +dup
    +ldc(string(message))
    +invokespecial(method(exceptionClass, "<init>", "(Ljava/lang/String;)V"))
    +athrow
    return start
}