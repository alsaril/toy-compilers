package com.alsaril.codegen

import com.alsaril.codegen.classfile.AccessFlag.*
import com.alsaril.codegen.code.*
import com.alsaril.codegen.code.ClassFileBuilder.Companion.BOOTSTRAP_PREFIX
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.instruction.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable

class DynamicConstantTest {
    @Test
    fun `builds a simple dynamic constant`() {
        // given
        val instance = classFile("Impl", parent = "java/lang/Object")
            .iface("java/util/concurrent/Callable")
            .method("<init>", "()V", PUBLIC) {
                +aload(0)
                invokespecial(parent(), "<init>", "()V")
                +`return`
            }
            .method("call", "()Ljava/lang/Object;", PUBLIC, FINAL) {
                +ldc(constantDynamic(self(), "bootstrap", "($BOOTSTRAP_PREFIX)Ljava/lang/Object;"))
                +areturn
            }
            .method("bootstrap", "($BOOTSTRAP_PREFIX)Ljava/lang/Object;", PRIVATE, STATIC, FINAL) {
                +ldc(string("Hello, world!"))
                +areturn
            }
            .build()
            .let { (name, code) -> ByteClassLoader().loadClass(name, code) }
            .getDeclaredConstructor()
            .newInstance() as Callable<*>

        // when / then
        assertThat(instance.call()).isEqualTo("Hello, world!")
    }

    @Test
    fun `builds a complex dynamic constant`() {
        // given
        val instance = classFile("Impl", parent = "java/lang/Object")
            .iface("java/util/concurrent/Callable")
            .method("<init>", "()V", PUBLIC) {
                +aload(0)
                invokespecial(parent(), "<init>", "()V")
                +`return`
            }
            .method("call", "()Ljava/lang/Object;", PUBLIC, FINAL) {
                val arg1 = string("plus")
                val arg2 = string("minus")
                +ldc(
                    constantDynamic(
                        self(),
                        "bootstrap",
                        "(${BOOTSTRAP_PREFIX}Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                        arg1,
                        arg2
                    )
                )
                +areturn
            }
            .method(
                "bootstrap",
                "(${BOOTSTRAP_PREFIX}Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                PRIVATE,
                STATIC,
                FINAL
            ) {
                +ldc(string("Hello, world! %s %s"))
                +iconst(2)
                +anewarray(clazz("java/lang/Object"))
                +dup
                +iconst(0)
                +aload(3)
                +aastore
                +dup
                +iconst(1)
                +aload(4)
                +aastore
                invokestatic(
                    clazz("java/lang/String"),
                    "format",
                    "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"
                )
                +areturn
            }
            .build()
            .let { (name, code) -> ByteClassLoader().loadClass(name, code) }
            .getDeclaredConstructor()
            .newInstance() as Callable<*>

        // when / then
        assertThat(instance.call()).isEqualTo("Hello, world! plus minus")
    }

    @Test
    fun `builds a recursive dynamic constant`() {
        // given
        val instance = classFile("Impl", parent = "java/lang/Object")
            .iface("java/util/concurrent/Callable")
            .method("<init>", "()V", PUBLIC) {
                +aload(0)
                invokespecial(parent(), "<init>", "()V")
                +`return`
            }
            .method("call", "()Ljava/lang/Object;", PUBLIC, FINAL) {
                val arg = constantDynamic(self(), "bootstrap1", "(${BOOTSTRAP_PREFIX})I")
                +ldc(constantDynamic(self(), "bootstrap2", "(${BOOTSTRAP_PREFIX}I)Ljava/lang/Object;", arg))
                +areturn
            }
            .method("bootstrap1", "(${BOOTSTRAP_PREFIX})I", PRIVATE, STATIC, FINAL) {
                +ldc(int(5))
                +ireturn
            }
            .method("bootstrap2", "(${BOOTSTRAP_PREFIX}I)Ljava/lang/Object;", PRIVATE, STATIC, FINAL) {
                +ldc(string("Hello, world!"))
                +iload(3)
                invokevirtual(clazz("java/lang/String"), "substring", "(I)Ljava/lang/String;")
                +areturn
            }
            .build()
            .let { (name, code) -> ByteClassLoader().loadClass(name, code) }
            .getDeclaredConstructor()
            .newInstance() as Callable<*>

        // when / then
        assertThat(instance.call()).isEqualTo(", world!")
    }

    @Test
    fun `loads a constant as the primitive its bootstrap method returns`() {
        // given the constant handed straight to ireturn, which takes nothing but an int
        val (name, bytes) = classFile("GenIntConstant", "java/lang/Object")
            .method("f", "()I", PUBLIC, STATIC) {
                +ldc(constantDynamic(self(), "bootstrap", "(${BOOTSTRAP_PREFIX})I"))
                +ireturn
            }
            .method("bootstrap", "(${BOOTSTRAP_PREFIX})I", PRIVATE, STATIC) {
                +iconst(5)
                +ireturn
            }
            .build()

        // then
        assertThat(ByteClassLoader().loadClass(name, bytes).getDeclaredMethod("f").invoke(null)).isEqualTo(5)
    }

    @Test
    fun `loads a constant as the class its bootstrap method returns`() {
        // given the constant used as a String, which the verifier checks against its declared type
        val (name, bytes) = classFile("GenStringConstant", "java/lang/Object")
            .method("f", "()I", PUBLIC, STATIC) {
                +ldc(constantDynamic(self(), "bootstrap", "(${BOOTSTRAP_PREFIX})Ljava/lang/String;"))
                invokevirtual(clazz("java/lang/String"), "length", "()I")
                +ireturn
            }
            .method("bootstrap", "(${BOOTSTRAP_PREFIX})Ljava/lang/String;", PRIVATE, STATIC) {
                +ldc(string("abc"))
                +areturn
            }
            .build()

        // then
        assertThat(ByteClassLoader().loadClass(name, bytes).getDeclaredMethod("f").invoke(null)).isEqualTo(3)
    }

    @Test
    fun `resolves a constant once, however often it is loaded`() {
        // given a bootstrap method handing out a new object on every call
        val (name, bytes) = classFile("GenResolvedOnce", "java/lang/Object")
            .method("f", "()I", PUBLIC, STATIC) {
                val constant = constantDynamic(self(), "bootstrap", "(${BOOTSTRAP_PREFIX})Ljava/lang/Object;")
                +ldc(constant)
                +ldc(constant)
                val same = +if_acmpeq
                +iconst(0)
                +ireturn
                link(same, +iconst(1))
                +ireturn
            }
            .method("bootstrap", "(${BOOTSTRAP_PREFIX})Ljava/lang/Object;", PRIVATE, STATIC) {
                constructDefault(clazz("java/lang/Object"))
                +areturn
            }
            .build()

        // then both loads give the one object the constant resolved to
        assertThat(ByteClassLoader().loadClass(name, bytes).getDeclaredMethod("f").invoke(null)).isEqualTo(1)
    }
}
