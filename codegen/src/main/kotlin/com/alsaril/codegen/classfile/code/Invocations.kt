package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.FIELD
import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.METHOD

fun CodeBuilder.method(classPointer: ClassPointer, name: String, descriptor: String): MethodDescriptor {
    val classNameIndex = cp.putClass(classPointer.name)
    val ref = cp.putRef(classNameIndex, name, descriptor, METHOD)
    return MethodDescriptor(ref)
}

fun CodeBuilder.field(classPointer: ClassPointer, name: String, descriptor: String): FieldDescriptor {
    val classNameIndex = cp.putClass(classPointer.name)
    val ref = cp.putRef(classNameIndex, name, descriptor, FIELD)
    return FieldDescriptor(ref)
}

// get
fun CodeBuilder.getstatic(fieldDescriptor: FieldDescriptor) {
    b1(0xb2)
    b2(fieldDescriptor.index)
}

// invoke
fun CodeBuilder.invokevirtual(methodDescriptor: MethodDescriptor) {
    b1(0xb6)
    b2(methodDescriptor.index)
}

fun CodeBuilder.invokespecial(methodDescriptor: MethodDescriptor) {
    b1(0xb7)
    b2(methodDescriptor.index)
}

fun CodeBuilder.invokestatic(methodDescriptor: MethodDescriptor) {
    b1(0xb8)
    b2(methodDescriptor.index)
}

fun CodeBuilder.invokeinterface(methodDescriptor: MethodDescriptor, count: Int) {
    b1(0xb9)
    b2(methodDescriptor.index)
    b1(count)
    b1(0)
}

// new
fun CodeBuilder.new(classIndex: Int) {
    b1(0xbb)
    b2(classIndex)
}

enum class ArrayType(val index: Int) {
    BYTE(8);
}

fun CodeBuilder.newarray(type: ArrayType) {
    b1(0xbc)
    b1(type.index)
}

fun CodeBuilder.construct(classPointer: ClassPointer, name: String, descriptor: String) {
    val idx = cp.putClass(classPointer.name)
    new(idx)
    dup()
    invokespecial(method(classPointer, name, descriptor))
}
