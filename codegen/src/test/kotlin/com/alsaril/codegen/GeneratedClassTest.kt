package com.alsaril.codegen

import com.alsaril.codegen.ByteClassLoader.loadClass
import com.alsaril.codegen.classfile.ClassFileBuilder
import com.alsaril.codegen.classfile.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.MethodAccessFlag.PUBLIC
import com.alsaril.codegen.classfile.MethodAccessFlag.STATIC
import com.alsaril.codegen.classfile.code.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
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

    @Test
    fun `runs a body that catches what it throws`() {
        // given a read that is in range for 0 and out of range for anything else
        val (name, bytes) = classFile("GenCatch", "java/lang/Object")
            .iface("java/util/function/IntUnaryOperator")
            .withConstructor()
            .method("applyAsInt", "(I)I", maxStack = 2, maxLocals = 3, PUBLIC) {
                iconst(1)
                newarray(ArrayType.BYTE)
                iload(1)

                val from = `try`()
                baload()
                val handler = `catch`(from, clazz("java/lang/ArrayIndexOutOfBoundsException"))
                val done = goto()

                handler(loc())
                frameStack(objInfo("java/lang/Throwable"))
                astore(2) // drop the throwable
                iconst(-1)

                done(loc())
                frameStack(IntInfo)
                ireturn()
            }
            .build()
        val function = loadClass(name, bytes)
            .getDeclaredConstructor().newInstance() as IntUnaryOperator

        // then the handler replaces the failure with -1, and the good path is untouched
        assertThat(function.applyAsInt(0)).isZero()
        assertThat(function.applyAsInt(1)).isEqualTo(-1)
        assertThat(function.applyAsInt(-1)).isEqualTo(-1)
    }

    @Test
    fun `runs a body whose handler does not cover what it throws`() {
        // given a range guarded against a different exception than the one raised
        val (name, bytes) = classFile("GenCatchType", "java/lang/Object")
            .iface("java/lang/Runnable")
            .withConstructor()
            .method("run", "()V", maxStack = 2, maxLocals = 2, PUBLIC) {
                val from = `try`()
                construct(clazz("java/lang/IllegalStateException"), "<init>", "()V")
                athrow()
                val handler = `catch`(from, clazz("java/lang/ArrayIndexOutOfBoundsException"))

                handler(loc())
                frameStack(objInfo("java/lang/Throwable"))
                astore(1)
                `return`()
            }
            .build()
        val instance = loadClass(name, bytes).getDeclaredConstructor()
            .newInstance() as Runnable

        // then catch_type keeps the handler out of the way
        assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy { instance.run() }
    }

    /**
     * The shape a finally block compiles to: one catch-all handler the normal path also
     * falls into, telling the two apart by a null pushed where the throwable would be.
     */
    @Test
    fun `runs a body that always reaches its handler`() {
        // given f(log, n), which throws for n == 0 and writes to log[0] either way
        val (name, bytes) = classFile("GenFinally", "java/lang/Object")
            .method("f", "([II)V", maxStack = 4, maxLocals = 2, PUBLIC, STATIC) {
                val from = `try`()
                iload(1)
                val ok = ifne()
                construct(clazz("java/lang/IllegalStateException"), "<init>", "()V")
                athrow()

                ok(loc())
                frameSame()
                aconst_null() // the normal path arrives with nothing to rethrow
                val handler = `catch`(from, type = null)

                handler(loc())
                frameStack(objInfo("java/lang/Throwable"))
                aload(0)
                iconst(0)
                iconst(1)
                iastore()

                dup()
                val exit = ifnull()
                athrow()

                exit(loc())
                frameStack(objInfo("java/lang/Throwable"))
                `return`()
            }
            .build()
        val method = loadClass(name, bytes)
            .getDeclaredMethod("f", IntArray::class.java, Int::class.javaPrimitiveType)

        // then the handler runs when the body returns
        val returned = IntArray(1)
        assertThatNoException().isThrownBy { method.invoke(null, returned, 1) }
        assertThat(returned).containsExactly(1)

        // and when it throws, without swallowing the failure
        val threw = IntArray(1)
        assertThatExceptionOfType(InvocationTargetException::class.java)
            .isThrownBy { method.invoke(null, threw, 0) }
            .withCauseInstanceOf(IllegalStateException::class.java)
        assertThat(threw).containsExactly(1)
    }
}
