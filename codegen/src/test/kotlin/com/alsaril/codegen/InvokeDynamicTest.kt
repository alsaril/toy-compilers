package com.alsaril.codegen

import com.alsaril.codegen.classfile.AccessFlag.*
import com.alsaril.codegen.code.*
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.INVOKE_STATIC
import com.alsaril.codegen.instruction.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InvokeDynamicTest {

    private fun call(argument: Int, members: ClassFileBuilder.() -> ClassFileBuilder): Any? {
        val (name, bytes) = classFile("GenIndy", parent = "java/lang/Object").members().build()
        return ByteClassLoader().loadClass(name, bytes).getDeclaredMethod("call", Int::class.java).invoke(null, argument)
    }

    @Test
    fun `links a call site to the handle its bootstrap method is given`() {
        // given a bootstrap method binding the site to the handle it takes, adapted to the type the site asks for
        val bind = "(${IDP}Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;"

        val result = call(4) {
            method("call", "(I)Ljava/lang/Object;", PUBLIC, STATIC) {
                +iload(0)
                val impl = methodHandle(INVOKE_STATIC, self(), "impl", "(I)Ljava/lang/String;")
                invokedynamic("_", "(I)Ljava/lang/Object;", bootstrap(methodHandle(INVOKE_STATIC, self(), "bind", bind), impl))
                +areturn
            }
            .method("bind", bind, PRIVATE, STATIC) {
                +new(clazz("java/lang/invoke/ConstantCallSite"))
                +dup
                +aload(3)
                +aload(2)
                invokevirtual(clazz("java/lang/invoke/MethodHandle"), "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;")
                invokespecial(clazz("java/lang/invoke/ConstantCallSite"), "<init>", "(Ljava/lang/invoke/MethodHandle;)V")
                +areturn
            }
            .method("impl", "(I)Ljava/lang/String;", PRIVATE, STATIC) {
                +ldc(string("Hello, world!"))
                +iload(0)
                invokevirtual(clazz("java/lang/String"), "substring", "(I)Ljava/lang/String;")
                +areturn
            }
        }

        assertThat(result).isEqualTo("o, world!")
    }

    @Test
    fun `makes a lambda capturing an argument through the metafactory`() {
        // given a Function made from impl with 10 captured as its first argument
        val result = call(42) {
            method("call", "(I)I", PUBLIC, STATIC) {
                +ldc(int(10))
                invokedynamic(
                    "apply",
                    "(I)Ljava/util/function/Function;",
                    lambdaBootstrap(
                        "(Ljava/lang/Object;)Ljava/lang/Object;",
                        methodHandle(INVOKE_STATIC, self(), "impl", "(II)I"),
                        "(Ljava/lang/Integer;)Ljava/lang/Integer;",
                    ),
                )
                +iload(0)
                invokestatic(clazz("java/lang/Integer"), "valueOf", "(I)Ljava/lang/Integer;")
                invokeinterface(clazz("java/util/function/Function"), "apply", "(Ljava/lang/Object;)Ljava/lang/Object;")
                +checkcast(clazz("java/lang/Integer"))
                invokevirtual(clazz("java/lang/Integer"), "intValue", "()I")
                +ireturn
            }
            .method("impl", "(II)I", PRIVATE, STATIC) {
                +iload(0)
                +iload(1)
                +iadd
                +ireturn
            }
        }

        assertThat(result).isEqualTo(52)
    }
}
