package com.alsaril.codegen.code

import com.alsaril.codegen.classfile.Type
import com.alsaril.codegen.classfile.parseFunctionDescriptor
import com.alsaril.codegen.classfile.parseType
import com.alsaril.codegen.constantpool.ClassPointer
import com.alsaril.codegen.constantpool.FieldDescriptor
import com.alsaril.codegen.constantpool.MethodDescriptor
import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.*
import com.alsaril.codegen.instruction.*


fun CodeBuilder.field(classPointer: ClassPointer, name: String, descriptor: String): FieldDescriptor {
    val ref = cp.putRef(classPointer.index, name, descriptor, FIELD)
    return FieldDescriptor(ref, ReferenceType(classPointer.name), parseType(descriptor).verificationType)
}

private fun CodeBuilder.extendSelf(args: List<Type>, dest: String) =
    listOf(ReferenceType(dest)) + args.map(Type::verificationType)

fun CodeBuilder.invokevirtual(classPointer: ClassPointer, name: String, descriptor: String) {
    val ref = cp.putRef(classPointer.index, name, descriptor, METHOD)
    val parsed = parseFunctionDescriptor(descriptor)
    val m = MethodDescriptor(ref, extendSelf(parsed.args, classPointer.name), parsed.returnType.verificationType, null)
    +invokevirtual(m)
}

fun CodeBuilder.invokespecial(classPointer: ClassPointer, name: String, descriptor: String) {
    val ref = cp.putRef(classPointer.index, name, descriptor, METHOD)
    val parsed = parseFunctionDescriptor(descriptor)
    val m = MethodDescriptor(
        ref,
        extendSelf(parsed.args, classPointer.name),
        parsed.returnType.verificationType,
        if (name == "<init>") ReferenceType(classPointer.name) else null
    )
    +invokespecial(m)
}

fun CodeBuilder.invokestatic(classPointer: ClassPointer, name: String, descriptor: String) {
    val ref = cp.putRef(classPointer.index, name, descriptor, METHOD)
    val parsed = parseFunctionDescriptor(descriptor)
    val m = MethodDescriptor(ref, parsed.args.map(Type::verificationType), parsed.returnType.verificationType, null)
    +invokestatic(m)
}

fun CodeBuilder.invokeinterface(classPointer: ClassPointer, name: String, descriptor: String) {
    val ref = cp.putRef(classPointer.index, name, descriptor, INTERFACE_METHOD)
    val parsed = parseFunctionDescriptor(descriptor)
    val m = MethodDescriptor(ref, extendSelf(parsed.args, classPointer.name), parsed.returnType.verificationType, null)
    +invokeinterface(m)
}

fun CodeBuilder.constructDefault(classPointer: ClassPointer) {
    +new(classPointer)
    +dup
    invokespecial(classPointer, "<init>", "()V")
}
