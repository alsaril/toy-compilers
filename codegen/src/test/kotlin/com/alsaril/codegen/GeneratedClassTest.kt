package com.alsaril.codegen

import com.alsaril.codegen.ByteClassLoader.loadClass
import com.alsaril.codegen.classfile.ClassFileBuilder
import com.alsaril.codegen.classfile.PrimitiveType
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
            .method("run", "()V", PUBLIC) { `return`() }
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
            .method("f", "(I)I", PUBLIC, STATIC) {
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
            .method("applyAsInt", "(I)I", PUBLIC) {
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
            .method("f", "(I)I", PUBLIC, STATIC) {
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
    fun `links a body spliced together from fragments`() {
        // given a fragment whose branches reach its own instructions
        val builder = classFile("GenSpliced", "java/lang/Object")
        val chooses = builder.emitFragment {
            iload(0)
            val otherwise = ifeq()
            iconst(1)
            val done = goto()

            val elseBranch = nop()
            link(otherwise, elseBranch)
            frameSame(elseBranch)
            iconst(2)

            val exit = ireturn()
            link(done, exit)
            frameStack(exit, IntInfo)
        }

        // when it is spliced in behind something else, so it does not land at zero
        val (name, bytes) = builder
            .method("f", "(I)I", PUBLIC, STATIC) {
                nop()
                fragment(chooses)
            }
            .build()
        val method = loadClass(name, bytes)
            .getDeclaredMethod("f", Int::class.javaPrimitiveType)

        // then which value comes back says the branch still reaches its own target
        assertThat(method.invoke(null, 0)).isEqualTo(2)
        assertThat(method.invoke(null, 1)).isEqualTo(1)
    }

    @Test
    fun `raises max_stack to the depth the body reaches`() {
        // given a body three deep, with the depth coming from the instructions alone
        val (name, bytes) = classFile("GenDeepStack", "java/lang/Object")
            .method("f", "()I", PUBLIC, STATIC) {
                iconst(1)
                iconst(1)
                iconst(1)
                iadd()
                iadd()
                ireturn()
            }
            .build()
        val method = loadClass(name, bytes).getDeclaredMethod("f")

        // then the verifier accepted a body three deep, so the derived depth covered it
        assertThatNoException().isThrownBy { method.invoke(null) }
    }

    @Test
    fun `resolves the class and constructor a new refers to`() {
        // given
        val (name, bytes) = classFile("GenThrows", "java/lang/Object")
            .iface("java/lang/Runnable")
            .withConstructor()
            .method("run", "()V", PUBLIC) {
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
            .method("run", "()V", PUBLIC) {
                iconst(0)
                val jump = ifeq()

                construct(clazz("java/lang/IllegalStateException"), "<init>", "()V")
                athrow()

                val target = `return`()
                link(jump, target)
                frameSame(target)
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
            .method("f", "(I)I", PUBLIC, STATIC) {
                iconst(2)
                istore(1)

                iload(0)
                val jump = ifeq()
                iconst(1)
                istore(1)

                val target = iload(1)
                link(jump, target)
                frameAppend(target, IntInfo)
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
            .method("f", "(I)F", PUBLIC, STATIC) {
                fconst(2)
                fstore(1)

                iload(0)
                val jump = ifeq()
                fconst(1)
                fstore(1)

                val target = fload(1)
                link(jump, target)
                frameAppend(target, FloatInfo)
                freturn()
            }
            .build()
        val method = loadClass(name, bytes)
            .getDeclaredMethod("f", Int::class.javaPrimitiveType)

        // then which value comes back says which path the jump offset chose
        assertThat(method.invoke(null, 0)).isEqualTo(2.0f)
        assertThat(method.invoke(null, 1)).isEqualTo(1.0f)
    }

    /**
     * f(args) = 1 when the branch is taken, 0 when it falls through, so which value comes
     * back says both that the operands were popped and that the offset reached its target.
     */
    private fun conditional(
        name: String,
        descriptor: String,
        operands: CodeBuilder.() -> Unit,
        jump: CodeBuilder.() -> Label,
    ): Class<*> {
        val (loaded, bytes) = classFile(name, "java/lang/Object")
            .method("f", descriptor, PUBLIC, STATIC) {
                operands()
                val branch = jump()

                iconst(0)
                val done = goto()

                val taken = iconst(1)
                link(branch, taken)
                frameSame(taken)

                val exit = ireturn()
                link(done, exit)
                frameStack(exit, IntInfo)
            }
            .build()
        return loadClass(loaded, bytes)
    }

    private fun againstZero(name: String, jump: CodeBuilder.() -> Label): (Int) -> Int {
        val method = conditional(name, "(I)I", { iload(0) }, jump)
            .getDeclaredMethod("f", Int::class.javaPrimitiveType)
        return { a -> method.invoke(null, a) as Int }
    }

    private fun betweenInts(name: String, jump: CodeBuilder.() -> Label): (Int, Int) -> Int {
        val method = conditional(name, "(II)I", { iload(0); iload(1) }, jump)
            .getDeclaredMethod("f", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        return { a, b -> method.invoke(null, a, b) as Int }
    }

    private fun againstNull(name: String, jump: CodeBuilder.() -> Label): (Any?) -> Int {
        val method = conditional(name, "(Ljava/lang/Object;)I", { aload(0) }, jump)
            .getDeclaredMethod("f", Any::class.java)
        return { r -> method.invoke(null, r) as Int }
    }

    @Test
    fun `branches on a comparison with zero`() {
        val ifeq = againstZero("GenIfeq") { ifeq() }
        assertThat(ifeq(0)).isOne()
        assertThat(ifeq(1)).isZero()

        val ifne = againstZero("GenIfne") { ifne() }
        assertThat(ifne(1)).isOne()
        assertThat(ifne(0)).isZero()

        val ifge = againstZero("GenIfge") { ifge() }
        assertThat(ifge(1)).isOne()
        assertThat(ifge(0)).isOne()
        assertThat(ifge(-1)).isZero()

        val ifgt = againstZero("GenIfgt") { ifgt() }
        assertThat(ifgt(1)).isOne()
        assertThat(ifgt(0)).isZero()
    }

    @Test
    fun `branches on a comparison between two ints`() {
        // these take two operands off the stack rather than one
        val lt = betweenInts("GenIfIcmplt") { if_icmplt() }
        assertThat(lt(1, 2)).isOne()
        assertThat(lt(1, 1)).isZero()
        assertThat(lt(2, 1)).isZero()

        val ge = betweenInts("GenIfIcmpge") { if_icmpge() }
        assertThat(ge(2, 1)).isOne()
        assertThat(ge(1, 1)).isOne()
        assertThat(ge(1, 2)).isZero()
    }

    @Test
    fun `branches on a comparison with null`() {
        val isNull = againstNull("GenIfnull") { ifnull() }
        assertThat(isNull(null)).isOne()
        assertThat(isNull("x")).isZero()

        val notNull = againstNull("GenIfnotnull") { ifnotnull() }
        assertThat(notNull("x")).isOne()
        assertThat(notNull(null)).isZero()
    }

    @Test
    fun `covers the range it guards with an exception handler`() {
        // given a read that is in range for 0 and out of range for anything else
        val (name, bytes) = classFile("GenCatch", "java/lang/Object")
            .iface("java/util/function/IntUnaryOperator")
            .withConstructor()
            .method("applyAsInt", "(I)I", PUBLIC) {
                iconst(1)
                newarray(PrimitiveType.BYTE)
                iload(1)

                val guarded = baload()
                val done = goto()

                val caught = astore(2) // drop the throwable
                `catch`(guarded, to = done, handler = caught, type = clazz("java/lang/ArrayIndexOutOfBoundsException"))
                frameStack(caught, objInfo("java/lang/Throwable"))
                iconst(-1)

                val exit = ireturn()
                link(done, exit)
                frameStack(exit, IntInfo)
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
            .method("run", "()V", PUBLIC) {
                val guarded = new(clazz("java/lang/IllegalStateException"))
                dup()
                invokespecial(method(clazz("java/lang/IllegalStateException"), "<init>", "()V"))
                athrow()

                val caught = astore(1)
                `catch`(guarded, to = caught, handler = caught, type = clazz("java/lang/ArrayIndexOutOfBoundsException"))
                frameStack(caught, objInfo("java/lang/Throwable"))
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
            .method("f", "([II)V", PUBLIC, STATIC) {
                val guarded = iload(1)
                val ok = ifne()
                construct(clazz("java/lang/IllegalStateException"), "<init>", "()V")
                athrow()

                val okTarget = aconst_null() // the normal path arrives with nothing to rethrow
                link(ok, okTarget)
                frameSame(okTarget)

                val caught = aload(0)
                `catch`(guarded, to = caught, handler = caught, type = null)
                frameStack(caught, objInfo("java/lang/Throwable"))
                iconst(0)
                iconst(1)
                iastore()

                dup()
                val exit = ifnull()
                athrow()

                val done = `return`()
                link(exit, done)
                frameStack(done, objInfo("java/lang/Throwable"))
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
    fun `takes an instance call on one arm of a branch`() {
        // given a call on one path only, so the two arms only agree on the depth where
        // they meet if the receiver was counted among the call's operands
        val (name, bytes) = classFile("GenBranchInvoke", "java/lang/Object")
            .method("f", "(Ljava/lang/String;I)I", PUBLIC, STATIC) {
                iconst(0)
                istore(2)

                iload(1)
                val jump = ifeq()
                aload(0)
                invokevirtual(method(clazz("java/lang/String"), "length", "()I"))
                istore(2)

                val target = iload(2)
                link(jump, target)
                frameAppend(target, IntInfo)
                ireturn()
            }
            .build()
        val method = loadClass(name, bytes)
            .getDeclaredMethod("f", String::class.java, Int::class.javaPrimitiveType)

        // then which value comes back says which path ran, and that both verified
        assertThat(method.invoke(null, "abcd", 0)).isEqualTo(0)
        assertThat(method.invoke(null, "abcd", 1)).isEqualTo(4)
    }

    @Test
    fun `resolves an interface method ref and the count it is called with`() {
        // given size() reached on an Object that has to be narrowed to a List first
        val (name, bytes) = classFile("GenCast", "java/lang/Object")
            .method("f", "(Ljava/lang/Object;)I", PUBLIC, STATIC) {
                aload(0)
                checkcast(clazz("java/util/List"))
                invokeinterface(imethod(clazz("java/util/List"), "size", "()I"))
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
