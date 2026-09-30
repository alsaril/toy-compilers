package com.alsaril.codegen.code

import com.alsaril.codegen.classfile.PrimitiveType.VOID
import com.alsaril.codegen.classfile.attributes.BootstrapMethod
import com.alsaril.codegen.classfile.parseFunctionDescriptor
import com.alsaril.codegen.constantpool.ClassPointer
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.INVOKE_STATIC
import com.alsaril.codegen.constantpool.DataPointer
import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.METHOD

const val CBP = "Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/Class;"

fun CodeBuilder.constantDynamic(clazz: ClassPointer, name: String, descriptor: String, vararg args: DataPointer): DataPointer {
    require(descriptor.startsWith("($CBP")) {
        "$name$descriptor cannot bootstrap a dynamic constant, which takes a lookup, a name and a type first"
    }
    val type = parseFunctionDescriptor(descriptor).returnType
    require(type != VOID) { "$name$descriptor returns void, so it has no constant to give" }

    val method = cp.putRef(clazz.index, name, descriptor, METHOD)
    val handle = cp.putConstantMethodHandleInfo(INVOKE_STATIC, method)
    val bootstrap = bootstrapMethods.add(BootstrapMethod(handle, args.map { it.index }))
    return DataPointer(cp.putConstantDynamicInfo("_", type.descriptor, bootstrap), type.verificationType)
}
