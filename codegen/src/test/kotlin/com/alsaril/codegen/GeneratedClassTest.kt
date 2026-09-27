package com.alsaril.codegen

import com.alsaril.codegen.ByteClassLoader.loadClass
import com.alsaril.codegen.code.ClassFileBuilder
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.AccessFlag.FINAL
import com.alsaril.codegen.classfile.AccessFlag.PRIVATE
import com.alsaril.codegen.classfile.AccessFlag.PUBLIC
import com.alsaril.codegen.classfile.AccessFlag.STATIC
import com.alsaril.codegen.classfile.PrimitiveType
import com.alsaril.codegen.code.*
import com.alsaril.codegen.instruction.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.function.IntUnaryOperator
import java.util.function.Supplier

class GeneratedClassTest {

    private fun ClassFileBuilder.withConstructor() = method(
        "<init>",
        "()V",
        PUBLIC,
    ) {
        +aload(0)
        invokespecial(parent(), "<init>", "()V")
        +`return`
    }

    @Test
    fun `links a class declaring an interface and a constructor`() {
        // given
        val (name, bytes) = classFile("GenRunnable", "java/lang/Object")
            .iface("java/lang/Runnable")
            .withConstructor()
            .method("run", "()V", PUBLIC) { +`return` }
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
                +iload(0)
                +ireturn
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
                +iload(1)
                +ireturn
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
                +iload(0)
                +istore(258)
                +iload(258)
                +ireturn
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
            +iload(0)
            val otherwise = +ifeq
            +iconst(1)
            val done = +goto

            val elseBranch = +nop
            link(otherwise, elseBranch)
            +iconst(2)

            val exit = +ireturn
            link(done, exit)
        }

        // when it is spliced in behind something else, so it does not land at zero
        val (name, bytes) = builder
            .method("f", "(I)I", PUBLIC, STATIC) {
                +nop
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
                +iconst(1)
                +iconst(1)
                +iconst(1)
                +iadd
                +iadd
                +ireturn
            }
            .build()
        val method = loadClass(name, bytes).getDeclaredMethod("f")

        // then the verifier accepted a body three deep, so the derived depth covered it
        assertThatNoException().isThrownBy { method.invoke(null) }
    }

    @Test
    fun `keeps a reference handed to the constructor in a field`() {
        // given a supplier that stores its constructor argument and hands it back
        val (name, bytes) = classFile("GenHolder", "java/lang/Object")
            .iface("java/util/function/Supplier")
            .field("value", "Ljava/lang/Object;", PRIVATE, FINAL)
            .method("<init>", "(Ljava/lang/Object;)V", PUBLIC) {
                +aload(0)
                invokespecial(parent(), "<init>", "()V")
                +aload(0)
                +aload(1)
                +putfield(field(self(), "value", "Ljava/lang/Object;"))
                +`return`
            }
            .method("get", "()Ljava/lang/Object;", PUBLIC) {
                +aload(0)
                +getfield(field(self(), "value", "Ljava/lang/Object;"))
                +areturn
            }
            .build()
        val held = Any()

        // when
        val supplier = loadClass(name, bytes)
            .getDeclaredConstructor(Any::class.java)
            .newInstance(held) as Supplier<*>

        // then the field was declared, written, read, and the reference came back as is
        assertThat(supplier.get()).isSameAs(held)
    }

    @Test
    fun `declares a field with the flags it was given`() {
        // given
        val (name, bytes) = classFile("GenFlags", "java/lang/Object")
            .field("a", "I", PRIVATE, FINAL)
            .field("b", "J", PUBLIC, STATIC)
            .field("c", "[Ljava/lang/String;")
            .build()

        // when
        val clazz = loadClass(name, bytes)

        // then
        with(clazz.getDeclaredField("a")) {
            assertThat(type).isEqualTo(Int::class.javaPrimitiveType)
            assertThat(modifiers).isEqualTo(Modifier.PRIVATE or Modifier.FINAL)
        }
        with(clazz.getDeclaredField("b")) {
            assertThat(type).isEqualTo(Long::class.javaPrimitiveType)
            assertThat(modifiers).isEqualTo(Modifier.PUBLIC or Modifier.STATIC)
        }
        with(clazz.getDeclaredField("c")) {
            assertThat(type).isEqualTo(Array<String>::class.java)
            assertThat(modifiers).isZero()
        }
    }

    @Test
    fun `stores and returns one value through dup_x1`() {
        // given the shape of `return this.last = v`: the copy goes under the receiver,
        // so it outlives the putfield and is what comes back
        val (name, bytes) = classFile("GenAssign", "java/lang/Object")
            .iface("java/util/function/IntUnaryOperator")
            .field("last", "I", PUBLIC)
            .withConstructor()
            .method("applyAsInt", "(I)I", PUBLIC) {
                +aload(0)
                +iload(1)
                +dup_x1
                +putfield(field(self(), "last", "I"))
                +ireturn
            }
            .build()
        val clazz = loadClass(name, bytes)
        val function = clazz.getDeclaredConstructor().newInstance() as IntUnaryOperator

        // when
        val result = function.applyAsInt(7)

        // then
        assertThat(result).isEqualTo(7)
        assertThat(clazz.getDeclaredField("last").getInt(function)).isEqualTo(7)
    }

    @Test
    fun `resolves the class and constructor a new refers to`() {
        // given
        val (name, bytes) = classFile("GenThrows", "java/lang/Object")
            .iface("java/lang/Runnable")
            .withConstructor()
            .method("run", "()V", PUBLIC) {
                constructDefault(clazz("java/lang/IllegalStateException"))
                +athrow
            }
            .build()
        val instance = loadClass(name, bytes).getDeclaredConstructor()
            .newInstance() as Runnable

        // then the pool entries named the class the body meant, and new/<init> verified
        assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy { instance.run() }
    }

    @Test
    fun `lands a branch on the instruction it was linked to`() {
        // given a body that jumps over the throw, so returning at all says the offset held
        val (name, bytes) = classFile("GenBranch", "java/lang/Object")
            .iface("java/lang/Runnable")
            .withConstructor()
            .method("run", "()V", PUBLIC) {
                +iconst(0)
                val jump = +ifeq

                constructDefault(clazz("java/lang/IllegalStateException"))
                +athrow

                val target = +`return`
                link(jump, target)
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
                +iconst(2)
                +istore(1)

                +iload(0)
                val jump = +ifeq
                +iconst(1)
                +istore(1)

                val target = +iload(1)
                link(jump, target)
                +ireturn
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
                +fconst(2)
                +fstore(1)

                +iload(0)
                val jump = +ifeq
                +fconst(1)
                +fstore(1)

                val target = +fload(1)
                link(jump, target)
                +freturn
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

                +iconst(0)
                val done = +goto

                val taken = +iconst(1)
                link(branch, taken)

                val exit = +ireturn
                link(done, exit)
            }
            .build()
        return loadClass(loaded, bytes)
    }

    private fun againstZero(name: String, jump: CodeBuilder.() -> Label): (Int) -> Int {
        val method = conditional(name, "(I)I", { +iload(0) }, jump)
            .getDeclaredMethod("f", Int::class.javaPrimitiveType)
        return { a -> method.invoke(null, a) as Int }
    }

    private fun betweenInts(name: String, jump: CodeBuilder.() -> Label): (Int, Int) -> Int {
        val method = conditional(name, "(II)I", { +iload(0); +iload(1) }, jump)
            .getDeclaredMethod("f", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        return { a, b -> method.invoke(null, a, b) as Int }
    }

    private fun againstNull(name: String, jump: CodeBuilder.() -> Label): (Any?) -> Int {
        val method = conditional(name, "(Ljava/lang/Object;)I", { +aload(0) }, jump)
            .getDeclaredMethod("f", Any::class.java)
        return { r -> method.invoke(null, r) as Int }
    }

    @Test
    fun `branches on a comparison with zero`() {
        val eq = againstZero("GenIfeq") { +ifeq }
        assertThat(eq(0)).isOne()
        assertThat(eq(1)).isZero()

        val ne = againstZero("GenIfne") { +ifne }
        assertThat(ne(1)).isOne()
        assertThat(ne(0)).isZero()

        val ge = againstZero("GenIfge") { +ifge }
        assertThat(ge(1)).isOne()
        assertThat(ge(0)).isOne()
        assertThat(ge(-1)).isZero()

        val gt = againstZero("GenIfgt") { +ifgt }
        assertThat(gt(1)).isOne()
        assertThat(gt(0)).isZero()
    }

    @Test
    fun `branches on a comparison between two ints`() {
        // these take two operands off the stack rather than one
        val lt = betweenInts("GenIfIcmplt") { +if_icmplt }
        assertThat(lt(1, 2)).isOne()
        assertThat(lt(1, 1)).isZero()
        assertThat(lt(2, 1)).isZero()

        val ge = betweenInts("GenIfIcmpge") { +if_icmpge }
        assertThat(ge(2, 1)).isOne()
        assertThat(ge(1, 1)).isOne()
        assertThat(ge(1, 2)).isZero()
    }

    @Test
    fun `branches on a comparison with null`() {
        val isNull = againstNull("GenIfnull") { +ifnull }
        assertThat(isNull(null)).isOne()
        assertThat(isNull("x")).isZero()

        val notNull = againstNull("GenIfnonnull") { +ifnonnull }
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
                +iconst(1)
                +newarray(PrimitiveType.BYTE)
                +iload(1)

                val guarded = +baload
                val done = +goto

                val caught = +astore(2) // drop the throwable
                `catch`(guarded, to = done, handler = caught, type = clazz("java/lang/ArrayIndexOutOfBoundsException"))
                +iconst(-1)

                val exit = +ireturn
                link(done, exit)
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
    fun `guards a range that runs to the end of the code`() {
        // given the handler emitted ahead of the range it covers, so nothing follows the
        // range and its end_pc is the length of the code
        val (name, bytes) = classFile("GenGuardToEnd", "java/lang/Object")
            .method("f", "(I)I", PUBLIC, STATIC) {
                val skip = +goto

                val caught = +astore(1) // drop the throwable
                +iconst(-1)
                +ireturn

                val guarded = +iconst(1)
                link(skip, guarded)
                +newarray(PrimitiveType.BYTE)
                +iload(0)
                +baload
                +ireturn

                `catch`(guarded, to = end(), handler = caught, type = null)
            }
            .build()
        val method = loadClass(name, bytes)
            .getDeclaredMethod("f", Int::class.javaPrimitiveType)

        // then the verifier took end_pc == code_length, and the throw still found the handler
        assertThat(method.invoke(null, 0)).isEqualTo(0)
        assertThat(method.invoke(null, 1)).isEqualTo(-1)
    }

    @Test
    fun `scopes a handler to the type it names`() {
        // given a range guarded against a different exception than the one raised
        val (name, bytes) = classFile("GenCatchType", "java/lang/Object")
            .iface("java/lang/Runnable")
            .withConstructor()
            .method("run", "()V", PUBLIC) {
                val guarded = +new(clazz("java/lang/IllegalStateException"))
                +dup
                invokespecial(clazz("java/lang/IllegalStateException"), "<init>", "()V")
                +athrow

                val caught = +astore(1)
                `catch`(guarded, to = caught, handler = caught, type = clazz("java/lang/ArrayIndexOutOfBoundsException"))
                +`return`
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
                val guarded = +iload(1)
                val ok = +ifne
                constructDefault(clazz("java/lang/IllegalStateException"))
                +athrow

                val okTarget = +aconst_null // the normal path arrives with nothing to rethrow
                link(ok, okTarget)

                val caught = +aload(0)
                `catch`(guarded, to = caught, handler = caught, type = null)
                +iconst(0)
                +iconst(1)
                +iastore

                +dup
                val exit = +ifnull
                +athrow

                val done = +`return`
                link(exit, done)
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
                +iconst(0)
                +istore(2)

                +iload(1)
                val jump = +ifeq
                +aload(0)
                invokevirtual(clazz("java/lang/String"), "length", "()I")
                +istore(2)

                val target = +iload(2)
                link(jump, target)
                +ireturn
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
                +aload(0)
                +checkcast(clazz("java/util/List"))
                invokeinterface(clazz("java/util/List"), "size", "()I")
                +ireturn
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

    @Test
    fun `settles a loop whose local changes type on the way round`() {
        // given f(n) looping n times, with slot 1 an int on the way in and a float on the way round
        val (name, bytes) = classFile("GenLoopWiden", "java/lang/Object")
            .method("f", "(I)I", PUBLIC, STATIC) {
                +iconst(0)
                +istore(1)

                val head = +iload(0)
                val exit = +ifeq
                +fconst(0)
                +fstore(1)
                +iinc(0, -1)
                val back = +goto

                val done = +iconst(7)
                +ireturn
                link(exit, done); link(back, head)
            }
            .build()
        val method = loadClass(name, bytes).getDeclaredMethod("f", Int::class.javaPrimitiveType)

        // then the frames past the loop agreed the slot is no longer an int
        assertThat(method.invoke(null, 0)).isEqualTo(7)
        assertThat(method.invoke(null, 3)).isEqualTo(7)
    }

    @Test
    fun `keeps the locals after a long argument where a branch target says they are`() {
        // given an int in slot 2, after the two slots of the long, read at a branch target
        val (name, bytes) = classFile("GenWideLocal", "java/lang/Object")
            .method("f", "(JI)I", PUBLIC, STATIC) {
                +iload(2)
                val jump = +ifeq
                +iconst(5)
                +istore(2)

                val target = +iload(2)
                link(jump, target)
                +ireturn
            }
            .build()
        val method = loadClass(name, bytes)
            .getDeclaredMethod("f", Long::class.javaPrimitiveType, Int::class.javaPrimitiveType)

        // then
        assertThat(method.invoke(null, 1L, 0)).isEqualTo(0)
        assertThat(method.invoke(null, 1L, 3)).isEqualTo(5)
    }

    @Test
    fun `lands a jump on the instruction right after it`() {
        // given a branch whose target is where the code would have gone anyway
        val (name, bytes) = classFile("GenNextTarget", "java/lang/Object")
            .method("f", "(I)I", PUBLIC, STATIC) {
                +iload(0)
                val jump = +ifeq
                link(jump, +iconst(1))
                +ireturn
            }
            .build()
        val method = loadClass(name, bytes).getDeclaredMethod("f", Int::class.javaPrimitiveType)

        // then it still needs a frame there, and has one
        assertThat(method.invoke(null, 0)).isEqualTo(1)
        assertThat(method.invoke(null, 1)).isEqualTo(1)
    }

    @Test
    fun `builds an object whose constructor argument a branch chooses`() {
        // given f(x) = new StringBuilder(x != 0 ? "yes" : "no").toString(), the new crossing the branch
        val builder = "java/lang/StringBuilder"
        val (name, bytes) = classFile("GenUninitialised", "java/lang/Object")
            .method("f", "(I)Ljava/lang/String;", PUBLIC, STATIC) {
                +new(clazz(builder))
                +dup
                +iload(0)
                val otherwise = +ifeq
                +ldc(string("yes"))
                val done = +goto

                link(otherwise, +ldc(string("no")))
                link(done, invokespecial(clazz(builder), "<init>", "(Ljava/lang/String;)V"))
                invokevirtual(clazz(builder), "toString", "()Ljava/lang/String;")
                +areturn
            }
            .build()
        val method = loadClass(name, bytes).getDeclaredMethod("f", Int::class.javaPrimitiveType)

        // then both paths met with the same uninitialised object on the stack
        assertThat(method.invoke(null, 1)).isEqualTo("yes")
        assertThat(method.invoke(null, 0)).isEqualTo("no")
    }

    @Test
    fun `branches before the parent constructor has run`() {
        // given a constructor that branches while this is still uninitialised
        val (name, bytes) = classFile("GenEarlyBranch", "java/lang/Object")
            .method("<init>", "(I)V", PUBLIC) {
                +aload(0)
                +iload(1)
                val skip = +ifeq
                +nop
                link(skip, invokespecial(parent(), "<init>", "()V"))
                +`return`
            }
            .build()
        val constructor = loadClass(name, bytes).getDeclaredConstructor(Int::class.javaPrimitiveType)

        // then
        assertThatNoException().isThrownBy { constructor.newInstance(0) }
        assertThatNoException().isThrownBy { constructor.newInstance(1) }
    }

    @Test
    fun `builds another object inside a constructor`() {
        // given a constructor that makes an Object after its own parent has run
        val (name, bytes) = classFile("GenNestedNew", "java/lang/Object")
            .method("<init>", "()V", PUBLIC) {
                +aload(0)
                invokespecial(parent(), "<init>", "()V")
                constructDefault(clazz("java/lang/Object"))
                +astore(1)
                +`return`
            }
            .build()

        // then the inner constructor call was told apart from the one on this
        assertThat(loadClass(name, bytes).getDeclaredConstructor().newInstance()).isNotNull()
    }

    @Test
    fun `keeps null in a local`() {
        // given
        val (name, bytes) = classFile("GenNullLocal", "java/lang/Object")
            .method("f", "()Ljava/lang/Object;", PUBLIC, STATIC) {
                +aconst_null
                +astore(0)
                +aload(0)
                +areturn
            }
            .build()

        // then
        assertThat(loadClass(name, bytes).getDeclaredMethod("f").invoke(null)).isNull()
    }

    @Test
    fun `guards a range that opens with a store`() {
        // given a handler covering the store to slot 0, so the throw may come before it
        val (name, bytes) = classFile("GenGuardedStore", "java/lang/Object")
            .method("f", "()I", PUBLIC, STATIC) {
                +iconst(5)
                val guarded = +istore(0)
                +iload(0)
                val end = +ireturn

                val caught = +iconst(-1)
                `catch`(guarded, to = end, handler = caught, type = null)
                +ireturn
            }
            .build()

        // then the handler frame did not count on slot 0
        assertThat(loadClass(name, bytes).getDeclaredMethod("f").invoke(null)).isEqualTo(5)
    }

    @Test
    fun `meets two unrelated classes on the stack as Object`() {
        // given f(x) = x != 0 ? Integer.valueOf(1) : "s"
        val (name, bytes) = classFile("GenMeetClasses", "java/lang/Object")
            .method("f", "(I)Ljava/lang/Object;", PUBLIC, STATIC) {
                +iload(0)
                val otherwise = +ifeq
                +iconst(1)
                invokestatic(clazz("java/lang/Integer"), "valueOf", "(I)Ljava/lang/Integer;")
                val done = +goto

                link(otherwise, +ldc(string("s")))
                link(done, +areturn)
            }
            .build()
        val method = loadClass(name, bytes).getDeclaredMethod("f", Int::class.javaPrimitiveType)

        // then
        assertThat(method.invoke(null, 1)).isEqualTo(1)
        assertThat(method.invoke(null, 0)).isEqualTo("s")
    }

    @Test
    fun `passes a subclass, an implementation and null where a class is declared`() {
        // given calls declared on Object and CharSequence, handed a String and a null
        val (name, bytes) = classFile("GenAssignable", "java/lang/Object")
            .method("hash", "()I", PUBLIC, STATIC) {
                +ldc(string("abc"))
                invokestatic(clazz("java/util/Objects"), "hashCode", "(Ljava/lang/Object;)I")
                +ireturn
            }
            .method("length", "()I", PUBLIC, STATIC) {
                +ldc(string("abc"))
                invokeinterface(clazz("java/lang/CharSequence"), "length", "()I")
                +ireturn
            }
            .method("hashNull", "()I", PUBLIC, STATIC) {
                +aconst_null
                invokestatic(clazz("java/util/Objects"), "hashCode", "(Ljava/lang/Object;)I")
                +ireturn
            }
            .build()
        val clazz = loadClass(name, bytes)

        // then
        assertThat(clazz.getDeclaredMethod("hash").invoke(null)).isEqualTo("abc".hashCode())
        assertThat(clazz.getDeclaredMethod("length").invoke(null)).isEqualTo(3)
        assertThat(clazz.getDeclaredMethod("hashNull").invoke(null)).isEqualTo(0)
    }

    @Test
    fun `compares references by identity`() {
        // given same(a, b) through if_acmpeq and different(a, b) through if_acmpne
        fun ClassFileBuilder.compare(name: String, jump: Instruction) =
            method(name, "(Ljava/lang/Object;Ljava/lang/Object;)I", PUBLIC, STATIC) {
                +aload(0)
                +aload(1)
                val taken = +jump
                +iconst(0)
                +ireturn
                link(taken, +iconst(1))
                +ireturn
            }
        val (name, bytes) = classFile("GenIdentity", "java/lang/Object")
            .compare("same", if_acmpeq)
            .compare("different", if_acmpne)
            .build()
        val clazz = loadClass(name, bytes)
        val same = clazz.getDeclaredMethod("same", Any::class.java, Any::class.java)
        val different = clazz.getDeclaredMethod("different", Any::class.java, Any::class.java)
        val one = Any()

        // then equal objects that are not the same one still differ
        assertThat(same.invoke(null, one, one)).isEqualTo(1)
        assertThat(same.invoke(null, one, Any())).isEqualTo(0)
        assertThat(different.invoke(null, one, one)).isEqualTo(0)
        assertThat(different.invoke(null, "a", String(charArrayOf('a')))).isEqualTo(1)
    }

    @Test
    fun `hands a long constant to a method taking one`() {
        // given
        val (name, bytes) = classFile("GenLongConstant", "java/lang/Object")
            .method("f", "()Ljava/lang/Object;", PUBLIC, STATIC) {
                +lconst(1)
                invokestatic(clazz("java/lang/Long"), "valueOf", "(J)Ljava/lang/Long;")
                +areturn
            }
            .build()

        // then
        assertThat(loadClass(name, bytes).getDeclaredMethod("f").invoke(null)).isEqualTo(1L)
    }

    @Test
    fun `copies two ints or one long with dup2`() {
        // given 2 + 3 + 2 + 3, and 1 + 1 as a long
        val (name, bytes) = classFile("GenDup2", "java/lang/Object")
            .method("ints", "()I", PUBLIC, STATIC) {
                +iconst(2)
                +iconst(3)
                +dup2
                +iadd
                +iadd
                +iadd
                +ireturn
            }
            .method("long", "()Ljava/lang/Object;", PUBLIC, STATIC) {
                +lconst(1)
                +dup2
                invokestatic(clazz("java/lang/Long"), "sum", "(JJ)J")
                invokestatic(clazz("java/lang/Long"), "valueOf", "(J)Ljava/lang/Long;")
                +areturn
            }
            .build()
        val clazz = loadClass(name, bytes)

        // then
        assertThat(clazz.getDeclaredMethod("ints").invoke(null)).isEqualTo(10)
        assertThat(clazz.getDeclaredMethod("long").invoke(null)).isEqualTo(2L)
    }

    @Test
    fun `tucks a copy of the top under the two below it with dup_x2`() {
        // given 1 2 3 becoming 3 1 2 3, then 3 - (1 - (2 - 3)), which only comes to 1 in that order
        val (name, bytes) = classFile("GenDupX2", "java/lang/Object")
            .method("f", "()I", PUBLIC, STATIC) {
                +iconst(1)
                +iconst(2)
                +iconst(3)
                +dup_x2
                +isub
                +isub
                +isub
                +ireturn
            }
            .build()

        // then
        assertThat(loadClass(name, bytes).getDeclaredMethod("f").invoke(null)).isEqualTo(1)
    }

    @Test
    fun `calls a private method and the parent's version of a method through invokespecial`() {
        // given a class overriding toString, calling past its override and into its private method
        val (name, bytes) = classFile("GenSpecial", "java/lang/Object")
            .withConstructor()
            .method("secret", "()I", PRIVATE) { +iconst(42); +ireturn }
            .method("viaPrivate", "()I", PUBLIC) {
                +aload(0)
                invokespecial(self(), "secret", "()I")
                +ireturn
            }
            .method("toString", "()Ljava/lang/String;", PUBLIC) { +ldc(string("overridden")); +areturn }
            .method("viaParent", "()Ljava/lang/String;", PUBLIC) {
                +aload(0)
                invokespecial(parent(), "toString", "()Ljava/lang/String;")
                +areturn
            }
            .build()
        val clazz = loadClass(name, bytes)
        val instance = clazz.getDeclaredConstructor().newInstance()

        // then neither call was taken for a constructor, and each reached the method it named
        assertThat(clazz.getDeclaredMethod("viaPrivate").invoke(instance)).isEqualTo(42)
        assertThat(instance.toString()).isEqualTo("overridden")
        assertThat(clazz.getDeclaredMethod("viaParent").invoke(instance) as String).startsWith("GenSpecial@")
    }

    @Test
    fun `keeps an object in a local until its constructor runs`() {
        // given an Object parked in slot 0 before it is initialised, and returned from there after
        val (name, bytes) = classFile("GenParkedNew", "java/lang/Object")
            .method("f", "()Ljava/lang/Object;", PUBLIC, STATIC) {
                +new(clazz("java/lang/Object"))
                +astore(0)
                +aload(0)
                invokespecial(clazz("java/lang/Object"), "<init>", "()V")
                +aload(0)
                +areturn
            }
            .build()

        // then the constructor initialised the copy in the local too
        assertThat(loadClass(name, bytes).getDeclaredMethod("f").invoke(null)).isNotNull()
    }

    @Test
    fun `keeps the locals after a stored long where a branch target says they are`() {
        // given a long stored to slots 1 and 2, with the int in slot 0 read at a branch target
        val (name, bytes) = classFile("GenStoredLong", "java/lang/Object")
            .method("f", "(I)I", PUBLIC, STATIC) {
                +lconst(1)
                +lstore(1)

                +iload(0)
                val jump = +ifeq
                +iconst(5)
                +istore(0)

                val target = +iload(0)
                link(jump, target)
                +ireturn
            }
            .build()
        val method = loadClass(name, bytes).getDeclaredMethod("f", Int::class.javaPrimitiveType)

        // then
        assertThat(method.invoke(null, 0)).isEqualTo(0)
        assertThat(method.invoke(null, 3)).isEqualTo(5)
    }

    @Test
    fun `reads a local each path left a different reference in`() {
        // given f(x) and g(x) storing to slot 1 on both arms and returning it after they meet:
        // null or a String in f, an Integer or a String in g
        fun ClassFileBuilder.storeEither(name: String, a: CodeBuilder.() -> Unit) =
            method(name, "(I)Ljava/lang/Object;", PUBLIC, STATIC) {
                +iload(0)
                val otherwise = +ifeq
                a()
                +astore(1)
                val done = +goto

                link(otherwise, +ldc(string("s")))
                +astore(1)
                link(done, +aload(1))
                +areturn
            }
        val (name, bytes) = classFile("GenMergedLocal", "java/lang/Object")
            .storeEither("f") { +aconst_null }
            .storeEither("g") {
                +iconst(1)
                invokestatic(clazz("java/lang/Integer"), "valueOf", "(I)Ljava/lang/Integer;")
            }
            .build()
        val clazz = loadClass(name, bytes)
        val f = clazz.getDeclaredMethod("f", Int::class.javaPrimitiveType)
        val g = clazz.getDeclaredMethod("g", Int::class.javaPrimitiveType)

        // then the slot was still a reference where it was read
        assertThat(f.invoke(null, 1)).isNull()
        assertThat(f.invoke(null, 0)).isEqualTo("s")
        assertThat(g.invoke(null, 1)).isEqualTo(1)
        assertThat(g.invoke(null, 0)).isEqualTo("s")
    }

    @Test
    fun `hands a handler the exception as the class it catches`() {
        // given f() throwing an UncheckedIOException and returning its IOException cause from the
        // handler, through getCause as UncheckedIOException declares it - a Throwable would not do
        val unchecked = "java/io/UncheckedIOException"
        val (name, bytes) = classFile("GenCaughtType", "java/lang/Object")
            .method("f", "()Ljava/lang/Object;", PUBLIC, STATIC) {
                val guarded = +new(clazz(unchecked))
                +dup
                +ldc(string("boom"))
                constructDefault(clazz("java/io/IOException"))
                invokespecial(clazz(unchecked), "<init>", "(Ljava/lang/String;Ljava/io/IOException;)V")
                +athrow

                val caught = invokevirtual(clazz(unchecked), "getCause", "()Ljava/io/IOException;")
                `catch`(guarded, to = caught, handler = caught, type = clazz(unchecked))
                +areturn
            }
            .build()

        // then
        assertThat(loadClass(name, bytes).getDeclaredMethod("f").invoke(null))
            .isInstanceOf(java.io.IOException::class.java)
    }
}
