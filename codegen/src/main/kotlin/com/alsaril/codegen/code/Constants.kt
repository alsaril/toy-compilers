package com.alsaril.codegen.code

import com.alsaril.codegen.classfile.PrimitiveType.VOID
import com.alsaril.codegen.classfile.parseType
import com.alsaril.codegen.constantpool.ClassPointer
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.*
import com.alsaril.codegen.constantpool.DataPointer
import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.*
import com.alsaril.codegen.verification.PrimitiveType.*
import com.alsaril.codegen.verification.ReferenceType

fun CodeBuilder.int(value: Int) = DataPointer(cp.putInt(value), INTEGER)

fun CodeBuilder.float(value: Float) = DataPointer(cp.putFloat(value), FLOAT)

fun CodeBuilder.long(value: Long) = DataPointer(cp.putLong(value), LONG)

fun CodeBuilder.double(value: Double) = DataPointer(cp.putDouble(value), DOUBLE)

fun CodeBuilder.string(value: String) = DataPointer(cp.putString(value), ReferenceType("java/lang/String"))

fun CodeBuilder.methodType(descriptor: String) =
    DataPointer(cp.putMethodType(descriptor), ReferenceType("java/lang/invoke/MethodType"))

fun CodeBuilder.methodHandle(
    kind: ReferenceKind,
    classPointer: ClassPointer,
    name: String,
    descriptor: String,
    onInterface: Boolean = false,
): DataPointer {
    require((kind == NEW_INVOKE_SPECIAL) == (name == "<init>")) {
        "a $kind handle to $name: only NEW_INVOKE_SPECIAL refers to <init>, and it refers to nothing else"
    }
    require(!onInterface || kind == INVOKE_STATIC || kind == INVOKE_SPECIAL || kind == INVOKE_INTERFACE) {
        "a $kind handle cannot refer to a method of an interface, only a static, a special or an interface one can"
    }
    val refType = when (kind) {
        GET_FIELD, GET_STATIC, PUT_FIELD, PUT_STATIC -> FIELD
        INVOKE_INTERFACE -> INTERFACE_METHOD
        INVOKE_STATIC, INVOKE_SPECIAL -> if (onInterface) INTERFACE_METHOD else METHOD
        INVOKE_VIRTUAL, NEW_INVOKE_SPECIAL -> METHOD
    }
    val handle = cp.putMethodHandle(kind, cp.putRef(classPointer.index, name, descriptor, refType))
    return DataPointer(handle, ReferenceType("java/lang/invoke/MethodHandle"))
}

fun CodeBuilder.constantDynamic(name: String, type: String, bootstrap: BootstrapPointer): DataPointer {
    val parsed = parseType(type)
    require(parsed != VOID) { "a dynamic constant of type $type would hold nothing" }
    return DataPointer(cp.putDynamic(name, type, bootstrap.index), parsed.verificationType)
}
