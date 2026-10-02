@file:MustUseReturnValues

package com.alsaril.codegen.code

import com.alsaril.codegen.classfile.Type
import com.alsaril.codegen.classfile.parseFunctionDescriptor
import com.alsaril.codegen.classfile.parseType
import com.alsaril.codegen.constantpool.ClassPointer
import com.alsaril.codegen.constantpool.FieldDescriptor
import com.alsaril.codegen.constantpool.MethodDescriptor
import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.*
import com.alsaril.codegen.instruction.*
import com.alsaril.codegen.verification.ReferenceType


fun CodeBuilder.field(classPointer: ClassPointer, name: String, descriptor: String): FieldDescriptor {
    val ref = cp.putRef(classPointer.index, name, descriptor, FIELD)
    return FieldDescriptor(ref, ReferenceType(classPointer.name), parseType(descriptor).verificationType)
}

private fun arguments(args: List<Type>, receiver: String?) =
    listOfNotNull(receiver?.let(::ReferenceType)) + args.map(Type::verificationType)

fun CodeBuilder.invokevirtual(classPointer: ClassPointer, name: String, descriptor: String): invokevirtual {
    val ref = cp.putRef(classPointer.index, name, descriptor, METHOD)
    val parsed = parseFunctionDescriptor(descriptor)
    val m = MethodDescriptor(ref, arguments(parsed.args, receiver = classPointer.name), parsed.returnType.verificationType, null)
    return invokevirtual(m)
}

fun CodeBuilder.invokespecial(classPointer: ClassPointer, name: String, descriptor: String): invokespecial {
    val ref = cp.putRef(classPointer.index, name, descriptor, METHOD)
    val parsed = parseFunctionDescriptor(descriptor)
    val m = MethodDescriptor(
        ref,
        arguments(parsed.args, receiver = classPointer.name),
        parsed.returnType.verificationType,
        if (name == "<init>") ReferenceType(classPointer.name) else null
    )
    return invokespecial(m)
}

fun CodeBuilder.invokestatic(classPointer: ClassPointer, name: String, descriptor: String): invokestatic {
    val ref = cp.putRef(classPointer.index, name, descriptor, METHOD)
    val parsed = parseFunctionDescriptor(descriptor)
    val m = MethodDescriptor(ref, arguments(parsed.args, receiver = null), parsed.returnType.verificationType, null)
    return invokestatic(m)
}

fun CodeBuilder.invokeinterface(classPointer: ClassPointer, name: String, descriptor: String): invokeinterface {
    val ref = cp.putRef(classPointer.index, name, descriptor, INTERFACE_METHOD)
    val parsed = parseFunctionDescriptor(descriptor)
    val m = MethodDescriptor(ref, arguments(parsed.args, receiver = classPointer.name), parsed.returnType.verificationType, null)
    return invokeinterface(m)
}

fun CodeBuilder.invokedynamic(name: String, descriptor: String, bootstrap: BootstrapPointer): invokedynamic {
    val ref = cp.putInvokeDynamic(name, descriptor, bootstrap.index)
    val parsed = parseFunctionDescriptor(descriptor)
    val m = MethodDescriptor(ref, arguments(parsed.args, receiver = null), parsed.returnType.verificationType, null)
    return invokedynamic(m)
}

fun CodeBuilder.constructDefault(classPointer: ClassPointer) {
    +new(classPointer)
    +dup
    +invokespecial(classPointer, "<init>", "()V")
}
