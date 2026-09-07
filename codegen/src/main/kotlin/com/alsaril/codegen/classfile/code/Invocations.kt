package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.FIELD
import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.METHOD

fun CodeBuilder.method(classPointer: ClassPointer, name: String, descriptor: String): MethodDescriptor {
    val ref = cp.putRef(classPointer.index, name, descriptor, METHOD)
    return MethodDescriptor(ref)
}

fun CodeBuilder.field(classPointer: ClassPointer, name: String, descriptor: String): FieldDescriptor {
    val ref = cp.putRef(classPointer.index, name, descriptor, FIELD)
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
fun CodeBuilder.new(classPointer: ClassPointer) {
    b1(0xbb)
    b2(classPointer.index)
}

enum class ArrayType(val index: Int) {
    BYTE(8), INT(10);
}

fun CodeBuilder.newarray(type: ArrayType) {
    b1(0xbc)
    b1(type.index)
}

fun CodeBuilder.construct(classPointer: ClassPointer, name: String, descriptor: String) {
    new(classPointer)
    dup()
    invokespecial(method(classPointer, name, descriptor))
}
