package com.alsaril.codegen.code

import com.alsaril.codegen.classfile.PrimitiveType.VOID
import com.alsaril.codegen.classfile.attributes.BootstrapMethod
import com.alsaril.codegen.classfile.parseFunctionDescriptor
import com.alsaril.codegen.classfile.parseType
import com.alsaril.codegen.constantpool.ClassPointer
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.*
import com.alsaril.codegen.constantpool.DataPointer
import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.*
import com.alsaril.codegen.verification.PrimitiveType.DOUBLE
import com.alsaril.codegen.verification.PrimitiveType.FLOAT
import com.alsaril.codegen.verification.PrimitiveType.INTEGER
import com.alsaril.codegen.verification.PrimitiveType.LONG
import com.alsaril.codegen.verification.ReferenceType

fun CodeBuilder.int(value: Int) = DataPointer(cp.putInt(value), INTEGER)

fun CodeBuilder.float(value: Float) = DataPointer(cp.putFloat(value), FLOAT)

fun CodeBuilder.long(value: Long) = DataPointer(cp.putLong(value), LONG)

fun CodeBuilder.double(value: Double) = DataPointer(cp.putDouble(value), DOUBLE)

fun CodeBuilder.string(value: String) = DataPointer(cp.putString(value), ReferenceType("java/lang/String"))

fun CodeBuilder.methodHandle(kind: ReferenceKind, classPointer: ClassPointer, name: String, descriptor: String): DataPointer {
    require((kind == NEW_INVOKE_SPECIAL) == (name == "<init>")) {
        "a $kind handle to $name: only NEW_INVOKE_SPECIAL refers to <init>, and it refers to nothing else"
    }
    val refType = when (kind) {
        GET_FIELD, GET_STATIC, PUT_FIELD, PUT_STATIC -> FIELD
        INVOKE_INTERFACE -> INTERFACE_METHOD
        INVOKE_VIRTUAL, INVOKE_STATIC, INVOKE_SPECIAL, NEW_INVOKE_SPECIAL -> METHOD
    }
    val handle = cp.putConstantMethodHandleInfo(kind, cp.putRef(classPointer.index, name, descriptor, refType))
    return DataPointer(handle, ReferenceType("java/lang/invoke/MethodHandle"))
}

const val CBP = "Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/Class;"

fun CodeBuilder.constantDynamic(
    clazz: ClassPointer,
    name: String,
    descriptor: String,
    vararg args: DataPointer,
    constantName: String = "_",
    constantType: String? = null,
): DataPointer {
    require(descriptor.startsWith("($CBP")) {
        "$name$descriptor cannot bootstrap a dynamic constant, which takes a lookup, a name and a type first"
    }
    val returnType = parseFunctionDescriptor(descriptor).returnType
    require(returnType != VOID) { "$name$descriptor returns void, so it has no constant to give" }
    val type = constantType?.let(::parseType) ?: returnType
    require(type != VOID) { "a dynamic constant of type $constantType would hold nothing" }

    val handle = methodHandle(INVOKE_STATIC, clazz, name, descriptor)
    val bootstrap = bootstrapMethods.add(BootstrapMethod(handle.index, args.map { it.index }))
    return DataPointer(cp.putConstantDynamicInfo(constantName, type.descriptor, bootstrap), type.verificationType)
}
