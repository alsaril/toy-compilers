package com.alsaril.codegen.classfile.code

data class ClassPointer(val index: Int)

data class MethodDescriptor(val index: Int)

data class FieldDescriptor(val index: Int)

data class DataPointer(val index: Int)

data class TryPointer(val index: Int)

fun CodeBuilder.self() = ClassPointer(cp.putClass(thisClass))

fun CodeBuilder.parent() = ClassPointer(cp.putClass(parentClass))

fun CodeBuilder.clazz(name: String) = ClassPointer(cp.putClass(name))

fun CodeBuilder.int(value: Int) = DataPointer(cp.putInt(value))

fun CodeBuilder.float(value: Float) = DataPointer(cp.putFloat(value))

fun CodeBuilder.string(value: String) = DataPointer(cp.putString(value))
