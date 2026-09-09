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
    fun `links a class declaring an interface and a constructor`() {
        // given
        val (name, bytes) = classFile("GenRunnable", "java/lang/Object")
            .iface("java/lang/Runnable")
            .withConstructor()
            .method("run", "()V", maxStack = 0, maxLocals = 1, PUBLIC) { `return`() }
            .build()

        // when
        val instance = loadClass(name, bytes).getDeclaredConstructor().newInstance()

        // then the interface entry and the constructor both resolved
        assertThat(instance).isInstanceOf(Runnable::class.java)
        assertThatNoException().isThrownBy { (instance as Runnable).run() }
    }

    @Test
    fun `puts the argument of a static method in slot zero`() {
        // given a body that returns its argument untouched, so the answer is the slot
        val (name, bytes) = classFile("GenStaticSlot", "java/lang/Object")
            .method("f", "(I)I", maxStack = 1, maxLocals = 1, PUBLIC, STATIC) {
                iload(0)
                ireturn()
            }
            .build()

        // when
        val method = loadClass(name, bytes)
            .getDeclaredMethod("f", Int::class.javaPrimitiveType)

        // then
        assertThat(method.invoke(null, 7)).isEqualTo(7)
    }

    @Test
    fun `puts the argument of an instance method in slot one`() {
        // given slot 0 is taken by this, so the argument lands one along
        val (name, bytes) = classFile("GenInstanceSlot", "java/lang/Object")
            .iface("java/util/function/IntUnaryOperator")
            .withConstructor()
            .method("applyAsInt", "(I)I", maxStack = 1, maxLocals = 2, PUBLIC) {
                iload(1)
                ireturn()
            }
            .build()

        // when
        val function = loadClass(name, bytes)
            .getDeclaredConstructor().newInstance() as IntUnaryOperator

        // then
        assertThat(function.applyAsInt(7)).isEqualTo(7)
    }

    @Test
    fun `reaches a local slot past the compact operand`() {
        // given a slot only the wide form can address, inside a max_locals that covers it
        val (name, bytes) = classFile("GenWideSlot", "java/lang/Object")
            .method("f", "(I)I", maxStack = 1, maxLocals = 300, PUBLIC, STATIC) {
                iload(0)
                istore(258)
                iload(258)
                ireturn()
            }
            .build()

        // when the round trip through slot 258 is all the body does, the answer is the slot
        val method = loadClass(name, bytes)
            .getDeclaredMethod("f", Int::class.javaPrimitiveType)

        // then
        assertThat(method.invoke(null, 7)).isEqualTo(7)
    }

    @Test
    fun `raises max_stack to the depth the body reaches`() {
        // given a method declaring no stack at all, with the depth coming from the body
        val (name, bytes) = classFile("GenDeepStack", "java/lang/Object")
            .method("f", "()I", maxStack = 0, maxLocals = 0, PUBLIC, STATIC) {
                maxStack(3)
                iconst(1)
                iconst(1)
                iconst(1)
                iadd()
                iadd()
                ireturn()
            }
            .build()
        val method = loadClass(name, bytes).getDeclaredMethod("f")

        // then the verifier accepted a body three deep, so the declared 0 was raised
        assertThatNoException().isThrownBy { method.invoke(null) }
    }

    @Test
    fun `resolves the class and constructor a new refers to`() {
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

        // then the pool entries named the class the body meant, and new/<init> verified
        assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy { instance.run() }
    }

    @Test
    fun `lands a branch on the offset its frame sits at`() {
        // given a body that jumps over the throw, so returning at all says the offset held
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
    fun `names an integer local a branch target carries`() {
        // given both paths writing slot 1 before they meet
        val (name, bytes) = classFile("GenIntFrame", "java/lang/Object")
            .method("f", "(I)I", maxStack = 1, maxLocals = 2, PUBLIC, STATIC) {
                iconst(2)
                istore(1)

                iload(0)
                val jump = ifeq()
                iconst(1)
                istore(1)

                jump(loc())
                frameAppend(IntInfo)
                iload(1)
                ireturn()
            }
            .build()
        val method = loadClass(name, bytes)
            .getDeclaredMethod("f", Int::class.javaPrimitiveType)

        // then which value comes back says which path the jump offset chose
        assertThat(method.invoke(null, 0)).isEqualTo(2)
        assertThat(method.invoke(null, 1)).isEqualTo(1)
    }

    @Test
    fun `names a float local a branch target carries`() {
        // given both paths writing slot 1 before they meet
        val (name, bytes) = classFile("GenFloatFrame", "java/lang/Object")
            .method("f", "(I)F", maxStack = 1, maxLocals = 2, PUBLIC, STATIC) {
                fconst(2)
                fstore(1)

                iload(0)
                val jump = ifeq()
                fconst(1)
                fstore(1)

                jump(loc())
                frameAppend(FloatInfo)
                fload(1)
                freturn()
            }
            .build()
        val method = loadClass(name, bytes)
            .getDeclaredMethod("f", Int::class.javaPrimitiveType)

        // then which value comes back says which path the jump offset chose
        assertThat(method.invoke(null, 0)).isEqualTo(2.0f)
        assertThat(method.invoke(null, 1)).isEqualTo(1.0f)
    }

    @Test
    fun `covers the range it guards with an exception handler`() {
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

        // then -1 says the handler offsets caught the throw, and 0 says the guarded
        // range ended where the body did
        assertThat(function.applyAsInt(0)).isZero()
        assertThat(function.applyAsInt(1)).isEqualTo(-1)
    }

    @Test
    fun `scopes a handler to the type it names`() {
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
    fun `reaches a catch all handler from both the normal and the failing path`() {
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

    @Test
    fun `resolves an interface method ref and the count it is called with`() {
        // given size() reached on an Object that has to be narrowed to a List first
        val (name, bytes) = classFile("GenCast", "java/lang/Object")
            .method("f", "(Ljava/lang/Object;)I", maxStack = 1, maxLocals = 1, PUBLIC, STATIC) {
                aload(0)
                checkcast(clazz("java/util/List"))
                invokeinterface(imethod(clazz("java/util/List"), "size", "()I"), count = 1)
                ireturn()
            }
            .build()
        val method = loadClass(name, bytes).getDeclaredMethod("f", Any::class.java)

        // then the ref resolved to the method it named, on the object handed in
        assertThat(method.invoke(null, listOf("a", "b"))).isEqualTo(2)

        // and the cast carried the class its operand pointed at
        assertThatExceptionOfType(InvocationTargetException::class.java)
            .isThrownBy { method.invoke(null, "not a list") }
            .withCauseInstanceOf(ClassCastException::class.java)
    }
}
