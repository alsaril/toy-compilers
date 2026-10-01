package com.alsaril.codegen.code

import com.alsaril.codegen.constantpool.ClassPointer

fun CodeBuilder.self() = clazz(thisClass)

fun CodeBuilder.parent() = clazz(parentClass)

fun CodeBuilder.clazz(name: String) = ClassPointer(cp.putClass(name), name)
