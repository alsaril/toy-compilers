package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.classfile.PrimitiveType
import com.alsaril.codegen.classfile.PrimitiveType.*
import com.alsaril.codegen.classfile.code.instruction.*
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

// get
fun CodeBuilder.getstatic(fieldDescriptor: FieldDescriptor) =
    add(GetStatic(fieldDescriptor.index, fieldDescriptor.slots))

// invoke
fun CodeBuilder.invokevirtual(methodDescriptor: MethodDescriptor) =
    add(InvokeVirtual(methodDescriptor.index, methodDescriptor.argSlots, methodDescriptor.returnSlots))

fun CodeBuilder.invokespecial(methodDescriptor: MethodDescriptor) =
    add(InvokeSpecial(methodDescriptor.index, methodDescriptor.argSlots, methodDescriptor.returnSlots))

fun CodeBuilder.invokestatic(methodDescriptor: MethodDescriptor) =
    add(InvokeStatic(methodDescriptor.index, methodDescriptor.argSlots, methodDescriptor.returnSlots))

fun CodeBuilder.invokeinterface(methodDescriptor: MethodDescriptor) =
    add(InvokeInterface(methodDescriptor.index, methodDescriptor.argSlots, methodDescriptor.returnSlots))

// class
fun CodeBuilder.new(classPointer: ClassPointer) = add(New(classPointer.index))

fun CodeBuilder.newarray(type: PrimitiveType) =
    NewArray(
        when (type) {
            BOOLEAN -> 4
            CHAR -> 5
            FLOAT -> 6
            DOUBLE -> 7
            BYTE -> 8
            SHORT -> 9
            INT -> 10
            LONG -> 11
            VOID -> throw IllegalArgumentException("an array cannot hold void")
        }
    ).let(::add)

fun CodeBuilder.checkcast(classPointer: ClassPointer) = add(CheckCast(classPointer.index))

fun CodeBuilder.instanceof(classPointer: ClassPointer) = add(InstanceOf(classPointer.index))

fun CodeBuilder.construct(classPointer: ClassPointer, name: String, descriptor: String) {
    new(classPointer)
    dup()
    invokespecial(method(classPointer, name, descriptor))
}
