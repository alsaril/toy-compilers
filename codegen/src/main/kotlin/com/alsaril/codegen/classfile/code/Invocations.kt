package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.*

fun CodeBuilder.method(classPointer: ClassPointer, name: String, descriptor: String): MethodDescriptor {
    val ref = cp.putRef(classPointer.index, name, descriptor, METHOD)
    return MethodDescriptor(ref)
}

fun CodeBuilder.imethod(classPointer: ClassPointer, name: String, descriptor: String): MethodDescriptor {
    val ref = cp.putRef(classPointer.index, name, descriptor, INTERFACE_METHOD)
    return MethodDescriptor(ref)
}

fun CodeBuilder.field(classPointer: ClassPointer, name: String, descriptor: String): FieldDescriptor {
    val ref = cp.putRef(classPointer.index, name, descriptor, FIELD)
    return FieldDescriptor(ref)
}

// get
fun CodeBuilder.getstatic(fieldDescriptor: FieldDescriptor) {
    u1(0xb2)
    u2(fieldDescriptor.index)
}

// invoke
fun CodeBuilder.invokevirtual(methodDescriptor: MethodDescriptor) {
    u1(0xb6)
    u2(methodDescriptor.index)
}

fun CodeBuilder.invokespecial(methodDescriptor: MethodDescriptor) {
    u1(0xb7)
    u2(methodDescriptor.index)
}

fun CodeBuilder.invokestatic(methodDescriptor: MethodDescriptor) {
    u1(0xb8)
    u2(methodDescriptor.index)
}

fun CodeBuilder.invokeinterface(methodDescriptor: MethodDescriptor, count: Int) {
    u1(0xb9)
    u2(methodDescriptor.index)
    u1(count)
    u1(0)
}

// class
fun CodeBuilder.new(classPointer: ClassPointer) {
    u1(0xbb)
    u2(classPointer.index)
}

enum class ArrayType(val index: Int) {
    BYTE(8), INT(10);
}

fun CodeBuilder.newarray(type: ArrayType) {
    u1(0xbc)
    u1(type.index)
}

fun CodeBuilder.checkcast(classPointer: ClassPointer) {
    u1(0xc0)
    u2(classPointer.index)
}

fun CodeBuilder.construct(classPointer: ClassPointer, name: String, descriptor: String) {
    new(classPointer)
    dup()
    invokespecial(method(classPointer, name, descriptor))
}
