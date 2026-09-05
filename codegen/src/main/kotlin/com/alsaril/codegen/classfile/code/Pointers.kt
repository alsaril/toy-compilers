package com.alsaril.codegen.classfile.code

data class ClassPointer(val name: String)

data class MethodDescriptor(val index: Int)

data class FieldDescriptor(val index: Int)

fun CodeBuilder.self() = ClassPointer(thisClass)

fun CodeBuilder.parent() = ClassPointer(parentClass)

fun CodeBuilder.clazz(name: String) = ClassPointer(name)
