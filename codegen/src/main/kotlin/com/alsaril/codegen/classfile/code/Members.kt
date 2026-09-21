package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.classfile.code.instruction.dup
import com.alsaril.codegen.classfile.code.instruction.invokespecial
import com.alsaril.codegen.classfile.code.instruction.new
import com.alsaril.codegen.classfile.parseFunctionDescriptor
import com.alsaril.codegen.classfile.parseType
import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.*

fun CodeBuilder.method(classPointer: ClassPointer, name: String, descriptor: String): MethodDescriptor {
    val ref = cp.putRef(classPointer.index, name, descriptor, METHOD)
    val parsed = parseFunctionDescriptor(descriptor)
    return MethodDescriptor(ref, parsed.argSlots(static = false), parsed.returnSlots())
}

fun CodeBuilder.smethod(classPointer: ClassPointer, name: String, descriptor: String): MethodDescriptor {
    val ref = cp.putRef(classPointer.index, name, descriptor, METHOD)
    val parsed = parseFunctionDescriptor(descriptor)
    return MethodDescriptor(ref, parsed.argSlots(static = true), parsed.returnSlots())
}

fun CodeBuilder.imethod(classPointer: ClassPointer, name: String, descriptor: String): MethodDescriptor {
    val ref = cp.putRef(classPointer.index, name, descriptor, INTERFACE_METHOD)
    val parsed = parseFunctionDescriptor(descriptor)
    return MethodDescriptor(ref, parsed.argSlots(static = false), parsed.returnSlots())
}

fun CodeBuilder.field(classPointer: ClassPointer, name: String, descriptor: String): FieldDescriptor {
    val ref = cp.putRef(classPointer.index, name, descriptor, FIELD)
    return FieldDescriptor(ref, parseType(descriptor).slots)
}

fun CodeBuilder.construct(classPointer: ClassPointer, name: String, descriptor: String) {
    +new(classPointer)
    +dup
    +invokespecial(method(classPointer, name, descriptor))
}
