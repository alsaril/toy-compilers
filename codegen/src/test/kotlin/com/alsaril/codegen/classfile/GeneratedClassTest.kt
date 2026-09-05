package com.alsaril.codegen.classfile

import com.alsaril.codegen.ByteClassLoader
import com.alsaril.codegen.ByteClassLoader.loadClass
import com.alsaril.codegen.bytesOf
import com.alsaril.codegen.classfile.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.MethodAccessFlag.PUBLIC
import com.alsaril.codegen.classfile.MethodAccessFlag.STATIC
import com.alsaril.codegen.classfile.code.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.function.IntUnaryOperator

class GeneratedClassTest {

    private fun ClassFileBuilder.withConstructor() = method(
        "<init>",
        "()V",
        maxStack = 1,
        maxLocals = 1,
        PUBLIC,
    ) {
        aload(0)
        invokespecial(method(parent(), "<init>", "()V"))
        `return`()
    }

    @Test
    fun `builds a class the jvm accepts as a Runnable`() {
        // given
        val (name, bytes) = classFile("GenRunnable", "java/lang/Object")
            .iface("java/lang/Runnable")
            .withConstructor()
            .method("run", "()V", maxStack = 0, maxLocals = 1, PUBLIC) { `return`() }
            .build()

        // when
        val instance = loadClass(name, bytes).getDeclaredConstructor().newInstance()

        // then
        assertThat(instance).isInstanceOf(Runnable::class.java)
        assertThatNoException().isThrownBy { (instance as Runnable).run() }
    }

    @Test
    fun `returns a computed value from a static method`() {
        // given
        val (name, bytes) = classFile("GenStatic", "java/lang/Object")
            .method("f", "()I", maxStack = 2, maxLocals = 0, PUBLIC, STATIC) {
                iconst(2)
                iconst(3)
                iadd()
                ireturn()
            }
            .build()

        // when
        val result = loadClass(name, bytes).getDeclaredMethod("f").invoke(null)

        // then
        assertThat(result).isEqualTo(5)
    }

    @Test
    fun `reads an argument out of a local slot`() {
        // given
        val (name, bytes) = classFile("GenTwice", "java/lang/Object")
            .method("twice", "(I)I", maxStack = 2, maxLocals = 1, PUBLIC, STATIC) {
                iload(0)
                iload(0)
                iadd()
                ireturn()
            }
            .build()

        // when
        val method = loadClass(name, bytes)
            .getDeclaredMethod("twice", Int::class.javaPrimitiveType)

        // then
        assertThat(method.invoke(null, 21)).isEqualTo(42)
        assertThat(method.invoke(null, -3)).isEqualTo(-6)
    }

    @Test
    fun `compiles a function instance that adds 42 to its argument`() {
        // given a class implementing IntUnaryOperator, so slot 0 is this and slot 1 the argument
        val (name, bytes) = classFile("GenAdder", "java/lang/Object")
            .iface("java/util/function/IntUnaryOperator")
            .withConstructor()
            .method("applyAsInt", "(I)I", maxStack = 2, maxLocals = 2, PUBLIC) {
                iload(1)
                iconst(42)
                iadd()
                ireturn()
            }
            .build()

        // then the body compiles to iload_1, bipush 42, iadd, ireturn
        assertThat(bytes).containsSequence(*bytesOf(0x1B, 0x10, 0x2A, 0x60, 0xAC))

        // and the loaded class behaves like the function it describes
        val function = loadClass(name, bytes)
            .getDeclaredConstructor().newInstance() as IntUnaryOperator

        assertThat(function.applyAsInt(0)).isEqualTo(42)
        assertThat(function.applyAsInt(1)).isEqualTo(43)
        assertThat(function.applyAsInt(-42)).isEqualTo(0)
    }

    @Test
    fun `runs a body that constructs and throws`() {
        // given
        val (name, bytes) = classFile("GenThrows", "java/lang/Object")
            .iface("java/lang/Runnable")
            .withConstructor()
            .method("run", "()V", maxStack = 2, maxLocals = 1, PUBLIC) {
                construct(clazz("java/lang/IllegalStateException"), "<init>", "()V")
                athrow()
            }
            .build()
        val instance = loadClass(name, bytes).getDeclaredConstructor()
            .newInstance() as Runnable

        // then
        assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy { instance.run() }
    }

    @Test
    fun `runs a body whose branch target carries a stack map frame`() {
        // given a body that jumps over the throw, so taking the branch returns normally
        val (name, bytes) = classFile("GenBranch", "java/lang/Object")
            .iface("java/lang/Runnable")
            .withConstructor()
            .method("run", "()V", maxStack = 2, maxLocals = 1, PUBLIC) {
                iconst(0)
                val jump = ifeq()

                construct(clazz("java/lang/IllegalStateException"), "<init>", "()V")
                athrow()

                jump(loc())
                frameSame()
                `return`()
            }
            .build()
        val instance = loadClass(name, bytes).getDeclaredConstructor()
            .newInstance() as Runnable

        // then
        assertThatNoException().isThrownBy { instance.run() }
    }
}
