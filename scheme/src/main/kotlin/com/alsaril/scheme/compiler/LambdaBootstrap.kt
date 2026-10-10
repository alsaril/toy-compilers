package com.alsaril.scheme.compiler

import com.alsaril.codegen.classfile.AccessFlag.*
import com.alsaril.codegen.code.*
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.INVOKE_STATIC
import com.alsaril.codegen.instruction.*
import com.alsaril.scheme.runtime.internalDescriptor
import com.alsaril.scheme.runtime.internalName
import java.lang.invoke.*
import java.lang.invoke.MethodHandles.Lookup.ClassOption.NESTMATE

object LambdaBootstrap {
    @JvmStatic
    fun bootstrap(
        lookup: MethodHandles.Lookup,
        name: String,
        methodType: MethodType, // indy descriptor: (captures) -> Function
        dest: MethodHandle,
        arity: Int,
    ): CallSite {
        val captures = methodType.parameterArray()
        val constructorDescriptor = methodType.changeReturnType(Void.TYPE)
        val bytes = classFile("L", "Ljava/lang/Object;")
            .iface("com/alsaril/scheme/runtime/Function")
            .apply {
                captures.forEachIndexed { index, clazz ->
                    field("c$index", clazz.descriptorString(), PRIVATE, FINAL)
                }
            }
            .method("<init>", constructorDescriptor.descriptorString(), PUBLIC) {
                +aload(0)
                +invokespecial(parent(), "<init>", "()V")

                captures.forEachIndexed { index, clazz ->
                    +aload(index + 1)
                    +putfield(field(self(), "c$index", clazz.descriptorString()))
                }

                +`return`
            }
            .method(internalName(arity), internalDescriptor(arity)) {
                val classData = methodHandle(
                    INVOKE_STATIC,
                    clazz("java/lang/invoke/MethodHandles"),
                    "classData",
                    "(${CBP})Ljava/lang/invoke/MethodHandle;"
                )
                +ldc(constantDynamic("_", "Ljava/lang/invoke/MethodHandle;", bootstrap(classData)))
                captures.forEachIndexed { index, clazz ->
                    +aload(index + 1)
                    +getfield(field(self(), "c$index", clazz.descriptorString()))
                }
                repeat(arity) {
                    +aload(it + 1)
                }
                val argsTypes = (1..arity).map { Any::class.java }
                val fullDescriptor = methodType.appendParameterTypes(argsTypes)
                +invokevirtual(clazz("java/lang/invoke/MethodHandle"), "invokeExact", fullDescriptor.descriptorString())
                +areturn
            }
            .build()

        val clazz = lookup.defineHiddenClassWithClassData(bytes, dest, true, NESTMATE).lookupClass()
        return ConstantCallSite(lookup.findConstructor(clazz, constructorDescriptor))
    }
}