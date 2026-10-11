package com.alsaril.scheme.compiler

import com.alsaril.codegen.classfile.AccessFlag.*
import com.alsaril.codegen.code.*
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.INVOKE_STATIC
import com.alsaril.codegen.debug.ClassDump
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
        val captures = methodType.parameterArray().drop(1)
        val constructorDescriptor = methodType.changeReturnType(Void.TYPE)
        val bytes = classFile("com/alsaril/scheme/compiler/L", "java/lang/Object")
            .iface("com/alsaril/scheme/runtime/Function")
            .field("row", "Ljava/lang/Object;", PRIVATE, FINAL)
            .apply {
                captures.forEachIndexed { index, clazz ->
                    field("c$index", clazz.descriptorString(), PRIVATE, FINAL)
                }
            }
            .method("<init>", constructorDescriptor.descriptorString(), PUBLIC) {
                +aload(0)
                +invokespecial(parent(), "<init>", "()V")

                +aload(0)
                +aload(1)
                +putfield(field(self(), "row", "Ljava/lang/Object;"))
                captures.forEachIndexed { index, clazz ->
                    +aload(0)
                    +aload(index + 2)
                    +putfield(field(self(), "c$index", clazz.descriptorString()))
                }

                +`return`
            }
            .method(internalName(arity), internalDescriptor(arity), PUBLIC) {
                val classData = methodHandle(
                    INVOKE_STATIC,
                    clazz("java/lang/invoke/MethodHandles"),
                    "classData",
                    "(${CBP})Ljava/lang/Object;"
                )
                +ldc(constantDynamic("_", "Ljava/lang/invoke/MethodHandle;", bootstrap(classData)))
                +aload(0)
                +getfield(field(self(), "row", "Ljava/lang/Object;"))
                captures.forEachIndexed { index, clazz ->
                    +aload(0)
                    +getfield(field(self(), "c$index", clazz.descriptorString()))
                }
                repeat(arity) {
                    +aload(it + 1)
                }
                val argsTypes = (1..arity).map { Any::class.java }
                val fullDescriptor = methodType.appendParameterTypes(argsTypes)
                +invokevirtual(
                    clazz("java/lang/invoke/MethodHandle"), "invokeExact", fullDescriptor.changeReturnType(
                        Any::class.java
                    ).descriptorString()
                )
                +areturn
            }
            .build()

        ClassDump.dump(bytes)

        val erased = dest.asType(dest.type().erase())
        val clazz = lookup.defineHiddenClassWithClassData(bytes, erased, true, NESTMATE).lookupClass()
        val mh = lookup.findConstructor(clazz, constructorDescriptor).asType(methodType)
        return ConstantCallSite(mh)
    }
}