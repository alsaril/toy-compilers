package com.alsaril.codegen.code

import com.alsaril.codegen.classfile.attributes.BootstrapMethod
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.INVOKE_STATIC
import com.alsaril.codegen.constantpool.DataPointer

class BootstrapMethods {
    private val bootstrapMethods = mutableMapOf<BootstrapMethod, Int>()

    fun isEmpty() = bootstrapMethods.isEmpty()

    fun add(bootstrapMethod: BootstrapMethod) =
        bootstrapMethods.computeIfAbsent(bootstrapMethod) { bootstrapMethods.size }

    fun methods() = bootstrapMethods.keys.toList()
}

data class BootstrapPointer(val index: Int)

const val CBP = "Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/Class;"

const val IDP = "Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"

fun CodeBuilder.bootstrap(handle: DataPointer, vararg args: DataPointer) =
    BootstrapPointer(bootstrapMethods.add(BootstrapMethod(handle.index, args.map { it.index })))

fun CodeBuilder.lambdaBootstrap(
    interfaceMethodType: String,
    implementation: DataPointer,
    dynamicMethodType: String,
): BootstrapPointer {
    val metafactory = methodHandle(
        INVOKE_STATIC,
        clazz("java/lang/invoke/LambdaMetafactory"),
        "metafactory",
        "(${IDP}Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
    )
    return bootstrap(metafactory, methodType(interfaceMethodType), implementation, methodType(dynamicMethodType))
}
