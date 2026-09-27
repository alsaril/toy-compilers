package com.alsaril.codegen.code

import com.alsaril.codegen.classfile.attributes.FullFrame
import com.alsaril.codegen.classfile.attributes.ObjectVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.DoubleVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.FloatVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.IntegerVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.LongVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.NullVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.TopVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.UninitializedThis
import com.alsaril.codegen.classfile.attributes.UninitializedVariableInfo
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import com.alsaril.codegen.instruction.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The frames the analyzer derives: where they go, and what they say about the locals and
 * the stack. A frame is keyed by the index of the instruction it describes until it is laid
 * out, so most expectations here name instructions rather than bytes; the last group checks
 * the layout. Whether the JVM accepts these frames is checked by loading classes, in
 * GeneratedClassTest.
 */
class FramesTest {

    private val cp = UpdatableConstantPool()

    private fun obj(name: String) = ObjectVariableInfo(cp.putClass(name))

    // the analyzer names each target once and in code order, however many jumps reach it
    private fun derived(
        descriptor: String = "()V",
        static: Boolean = true,
        constructor: Boolean = false,
        hierarchy: ClassHierarchy = LenientHierarchy,
        body: CodeBuilder.() -> Unit,
    ) = analysis(cp, descriptor, static, constructor, hierarchy, body).third

    @Nested
    inner class Placement {

        @Test
        fun `records no frame for straight line code`() {
            assertThat(derived("()I") { +iconst(1); +ireturn }).isEmpty()
        }

        @Test
        fun `puts a frame on the target of a conditional jump, not on the fall through`() {
            // given
            val frames = derived {
                +iconst(0)
                val jump = +ifeq
                +`return`
                val target = +`return` // 3
                link(jump, target)
            }

            // then only the target needs one: the fall through follows from the code before it
            assertThat(frames).containsExactly(FullFrame(3, emptyList(), emptyList()))
        }

        @Test
        fun `puts a frame on the target of a goto`() {
            // given a goto over the instruction the conditional jump lands on
            val frames = derived {
                +iconst(0)
                val jump = +ifeq
                val skip = +goto
                link(jump, +nop)          // 3
                link(skip, +`return`)     // 4
            }

            // then
            assertThat(frames).containsExactly(
                FullFrame(3, emptyList(), emptyList()),
                FullFrame(4, emptyList(), emptyList()),
            )
        }

        @Test
        fun `puts a frame on a target that is also the next instruction`() {
            // a jump that lands where the code would have gone anyway is still a branch target
            assertThat(derived { +iconst(0); val jump = +ifeq; link(jump, +`return`) })
                .containsExactly(FullFrame(2, emptyList(), emptyList()))
            assertThat(derived { val jump = +goto; link(jump, +`return`) })
                .containsExactly(FullFrame(1, emptyList(), emptyList()))
        }

        @Test
        fun `puts a frame on a handler entry, holding the throwable alone`() {
            // given
            val frames = derived {
                val guarded = +aconst_null
                +athrow
                val caught = +astore(0) // 2
                `catch`(guarded, to = caught, handler = caught, type = null)
                +`return`
            }

            // then
            assertThat(frames).containsExactly(FullFrame(2, emptyList(), listOf(obj("java/lang/Throwable"))))
        }

        @Test
        fun `puts the class a row catches on its handler's stack`() {
            // given
            val frames = derived {
                val guarded = +aconst_null
                +athrow
                val caught = +astore(0) // 2
                `catch`(guarded, to = caught, handler = caught, type = clazz("java/lang/IllegalStateException"))
                +`return`
            }

            // then
            assertThat(frames).containsExactly(FullFrame(2, emptyList(), listOf(obj("java/lang/IllegalStateException"))))
        }

        // two rows sending one range to one handler, catching different classes, as a multi-catch does
        private fun CodeBuilder.catchEither() {
            val guarded = +aconst_null
            +athrow
            val caught = +astore(0) // 2
            `catch`(guarded, to = caught, handler = caught, type = clazz("java/lang/IllegalStateException"))
            `catch`(guarded, to = caught, handler = caught, type = clazz("java/lang/IllegalArgumentException"))
            +`return`
        }

        @Test
        fun `meets the classes two rows catch at one handler as the hierarchy names`() {
            // given a hierarchy that knows both are runtime exceptions
            val hierarchy = object : ClassHierarchy {
                override fun isAssignable(from: String, to: String) = true
                override fun commonSuperclass(a: String, b: String) = "java/lang/RuntimeException"
            }

            // then
            assertThat(derived(hierarchy = hierarchy) { catchEither() })
                .containsExactly(FullFrame(2, emptyList(), listOf(obj("java/lang/RuntimeException"))))
        }

        @Test
        fun `meets the classes two rows catch at one handler as Object when the hierarchy knows neither`() {
            assertThat(derived { catchEither() })
                .containsExactly(FullFrame(2, emptyList(), listOf(obj("java/lang/Object"))))
        }
    }

    @Nested
    inner class Locals {

        // a jump over one nop to whatever is emitted next, so that instruction needs a frame
        private fun CodeBuilder.jumpOver() {
            +iconst(0)
            val jump = +ifeq
            +nop
            link(jump, end())
        }

        @Test
        fun `start a static method with its arguments`() {
            assertThat(derived("(ILjava/lang/String;F[I)V") { jumpOver(); +`return` }).containsExactly(
                FullFrame(
                    3,
                    listOf(IntegerVariableInfo, obj("java/lang/String"), FloatVariableInfo, obj("[I")),
                    emptyList(),
                ),
            )
        }

        @Test
        fun `start an instance method with this`() {
            assertThat(derived("(I)V", static = false) { jumpOver(); +`return` })
                .containsExactly(FullFrame(3, listOf(obj(THIS_CLASS), IntegerVariableInfo), emptyList()))
        }

        @Test
        fun `start a constructor with this not yet initialised`() {
            // given a branch before the parent constructor runs
            val frames = derived(static = false, constructor = true) {
                jumpOver()
                +aload(0) // 3
                invokespecial(parent(), "<init>", "()V")
                +`return`
            }

            // then
            assertThat(frames).containsExactly(FullFrame(3, listOf(UninitializedThis), emptyList()))
        }

        @Test
        fun `hold this as the class once the parent constructor has run`() {
            // given
            val frames = derived(static = false, constructor = true) {
                +aload(0)
                invokespecial(parent(), "<init>", "()V")
                jumpOver()
                +`return` // 5
            }

            // then
            assertThat(frames).containsExactly(FullFrame(5, listOf(obj(THIS_CLASS)), emptyList()))
        }

        @Test
        fun `write a long or a double as one entry covering both of its slots`() {
            // a two slot value is a single verification type, so nothing may follow it for its
            // second half - an extra top would move every later local one slot up
            assertThat(derived("(JDI)V") { jumpOver(); +`return` }).containsExactly(
                FullFrame(3, listOf(LongVariableInfo, DoubleVariableInfo, IntegerVariableInfo), emptyList()),
            )
        }

        @Test
        fun `write a stored long as one entry covering both of its slots`() {
            assertThat(derived("(I)V") { +lconst(1); +lstore(1); jumpOver(); +`return` })
                .containsExactly(FullFrame(5, listOf(IntegerVariableInfo, LongVariableInfo), emptyList()))
        }

        @Test
        fun `forget a long when a store overwrites either of its halves`() {
            // a long cut in half is neither half a long, so its first slot is left as top
            assertThat(derived("(J)V") { +iconst(1); +istore(0); jumpOver(); +`return` })
                .containsExactly(FullFrame(5, listOf(IntegerVariableInfo, TopVariableInfo), emptyList()))
            assertThat(derived("(J)V") { +iconst(1); +istore(1); jumpOver(); +`return` })
                .containsExactly(FullFrame(5, listOf(TopVariableInfo, IntegerVariableInfo), emptyList()))
            assertThat(derived("(J)V") { +aconst_null; +astore(1); jumpOver(); +`return` })
                .containsExactly(FullFrame(5, listOf(TopVariableInfo, NullVariableInfo), emptyList()))
        }

        @Test
        fun `forget a long when another long is stored across its second half`() {
            assertThat(derived("(J)V") { +lconst(1); +lstore(1); jumpOver(); +`return` })
                .containsExactly(FullFrame(5, listOf(TopVariableInfo, LongVariableInfo), emptyList()))
        }

        @Test
        fun `keep an object fresh from new in a local until its constructor runs`() {
            // given the object parked in slot 0 across one jump before its constructor and one after
            val frames = derived {
                +new(clazz("java/lang/Object"))
                +astore(0)
                jumpOver()
                +aload(0) // 5
                invokespecial(clazz("java/lang/Object"), "<init>", "()V")
                jumpOver()
                +`return` // 10
            }

            // then the local is initialised in place, like every copy on the stack
            assertThat(frames).containsExactly(
                FullFrame(5, listOf(UninitializedVariableInfo(0)), emptyList()),
                FullFrame(10, listOf(obj("java/lang/Object")), emptyList()),
            )
        }

        @Test
        fun `take the type each store left behind`() {
            // given
            val frames = derived {
                +iconst(1)
                +istore(0)
                +fconst(1)
                +fstore(1)
                +aconst_null
                +astore(2)
                jumpOver()
                +`return` // 9
            }

            // then
            assertThat(frames).containsExactly(
                FullFrame(9, listOf(IntegerVariableInfo, FloatVariableInfo, NullVariableInfo), emptyList()),
            )
        }

        @Test
        fun `fill the slots a store skips over with top`() {
            assertThat(derived { +iconst(1); +istore(2); jumpOver(); +`return` })
                .containsExactly(FullFrame(5, listOf(TopVariableInfo, TopVariableInfo, IntegerVariableInfo), emptyList()))
        }

        @Test
        fun `widen a local the paths disagree on to top`() {
            // given slot 0 an int on the jump and a float on the fall through
            val frames = derived {
                +iconst(1)
                +istore(0)
                +iconst(0)
                val jump = +ifeq
                +fconst(1)
                +fstore(0)
                val target = +`return` // 6
                link(jump, target)
            }

            // then
            assertThat(frames).containsExactly(FullFrame(6, listOf(TopVariableInfo), emptyList()))
        }

        // slot 1 given a value by each arm of `x == 0 ? a : b`, the arms meeting at a return
        private fun CodeBuilder.storeEither(a: CodeBuilder.() -> Unit, b: CodeBuilder.() -> Unit) {
            +iload(0)
            val otherwise = +ifeq
            a()
            val done = +goto
            link(otherwise, end())
            b()
            link(done, +`return`)
        }

        private val integer: CodeBuilder.() -> Unit = {
            +iconst(1)
            invokestatic(clazz("java/lang/Integer"), "valueOf", "(I)Ljava/lang/Integer;")
            +astore(1)
        }

        @Test
        fun `meet null and a reference in a local as the reference`() {
            // given
            val frames = derived("(I)V") {
                storeEither({ +aconst_null; +astore(1) }, { +ldc(string("s")); +astore(1) })
            }

            // then
            assertThat(frames).containsExactly(
                FullFrame(5, listOf(IntegerVariableInfo), emptyList()),
                FullFrame(7, listOf(IntegerVariableInfo, obj("java/lang/String")), emptyList()),
            )
        }

        @Test
        fun `meet two classes in a local at the one the hierarchy names`() {
            // given a hierarchy that knows what an Integer and a String have in common
            val hierarchy = object : ClassHierarchy {
                override fun isAssignable(from: String, to: String) = true
                override fun commonSuperclass(a: String, b: String) = "java/io/Serializable"
            }
            val body: CodeBuilder.() -> Unit = { storeEither(integer, { +ldc(string("s")); +astore(1) }) }

            // then as Object when the hierarchy knows neither class, and as what it names when it does
            assertThat(derived("(I)V", body = body).last())
                .isEqualTo(FullFrame(8, listOf(IntegerVariableInfo, obj("java/lang/Object")), emptyList()))
            assertThat(derived("(I)V", hierarchy = hierarchy, body = body).last())
                .isEqualTo(FullFrame(8, listOf(IntegerVariableInfo, obj("java/io/Serializable")), emptyList()))
        }

        @Test
        fun `widen a local to top where a reference meets a number`() {
            // given an int on one arm and a String on the other, which is no error until it is read
            val frames = derived("(I)V") {
                storeEither({ +iconst(1); +istore(1) }, { +ldc(string("s")); +astore(1) })
            }

            // then
            assertThat(frames.last()).isEqualTo(FullFrame(7, listOf(IntegerVariableInfo, TopVariableInfo), emptyList()))
        }

        @Test
        fun `drop a local that only one path defines`() {
            // given slot 0 written on the fall through only
            val frames = derived {
                +iconst(0)
                val jump = +ifeq
                +iconst(1)
                +istore(0)
                val target = +`return` // 4
                link(jump, target)
            }

            // then
            assertThat(frames).containsExactly(FullFrame(4, emptyList(), emptyList()))
        }

        @Test
        fun `settle a loop whose local changes type on the way round`() {
            // given slot 1 an int on entry and a float on the back edge
            val frames = derived("(I)V") {
                +iconst(0)
                +istore(1)
                val head = +iload(0) // 2
                val exit = +ifeq
                +fconst(0)
                +fstore(1)
                val back = +goto
                val done = +`return` // 7
                link(exit, done); link(back, head)
            }

            // then the widened slot reaches past the loop too, not only its head
            assertThat(frames).containsExactly(
                FullFrame(2, listOf(IntegerVariableInfo, TopVariableInfo), emptyList()),
                FullFrame(7, listOf(IntegerVariableInfo, TopVariableInfo), emptyList()),
            )
        }

        @Test
        fun `give a handler the locals from before a guarded store ran`() {
            // given a range that opens with the store to slot 0
            val frames = derived {
                +iconst(5)
                val guarded = +istore(0)
                +aconst_null
                +athrow
                val caught = +astore(1) // 4
                `catch`(guarded, to = caught, handler = caught, type = null)
                +`return`
            }

            // then the throw may come before the store, so the handler cannot count on slot 0
            assertThat(frames).containsExactly(FullFrame(4, emptyList(), listOf(obj("java/lang/Throwable"))))
        }

        @Test
        fun `give a handler the locals from after a guarded constructor call as well`() {
            // given the object parked in slot 0, initialised by the only guarded instruction
            val frames = derived {
                +new(clazz("java/lang/Object"))
                +astore(0)
                +aload(0)
                val guarded = invokespecial(clazz("java/lang/Object"), "<init>", "()V")
                val end = +`return`
                val caught = +`return` // 5
                `catch`(guarded, to = end, handler = caught, type = null)
            }

            // then slot 0 is uninitialised before the call and the class after it, which only top covers
            assertThat(frames).containsExactly(
                FullFrame(5, listOf(TopVariableInfo), listOf(obj("java/lang/Throwable"))),
            )
        }

        @Test
        fun `give a handler a local the whole range agrees on`() {
            // given
            val frames = derived {
                +iconst(5)
                +istore(0)
                val guarded = +aconst_null
                +athrow
                val caught = +astore(1) // 4
                `catch`(guarded, to = caught, handler = caught, type = null)
                +`return`
            }

            // then
            assertThat(frames)
                .containsExactly(FullFrame(4, listOf(IntegerVariableInfo), listOf(obj("java/lang/Throwable"))))
        }
    }

    @Nested
    inner class Stack {

        @Test
        fun `carries the values left under a jump to its target`() {
            // given a seven the jump leaves behind
            val frames = derived("()I") {
                +iconst(7)
                +iconst(0)
                val jump = +ifeq
                +nop
                val target = +ireturn // 4
                link(jump, target)
            }

            // then
            assertThat(frames).containsExactly(FullFrame(4, emptyList(), listOf(IntegerVariableInfo)))
        }

        @Test
        fun `types every value on the stack`() {
            // given
            val frames = derived {
                +fconst(1)
                +lconst(1)
                +ldc(string("s"))
                +aconst_null
                +iconst(0)
                val jump = +ifeq
                +nop
                val target = +`return` // 7
                link(jump, target)
            }

            // then a long is one entry on the stack as well
            assertThat(frames).containsExactly(
                FullFrame(
                    7,
                    emptyList(),
                    listOf(FloatVariableInfo, LongVariableInfo, obj("java/lang/String"), NullVariableInfo),
                ),
            )
        }

        // the two arms of `x == 0 ? a : b` leave one value each where they meet at areturn
        private fun CodeBuilder.choose(a: CodeBuilder.() -> Unit, b: CodeBuilder.() -> Unit) {
            +iload(0)
            val otherwise = +ifeq
            a()
            val done = +goto
            link(otherwise, end())
            b()
            link(done, +areturn)
        }

        @Test
        fun `meets null and a reference as the reference`() {
            // given
            val frames = derived("(ILjava/lang/String;)Ljava/lang/Object;") {
                choose({ +aconst_null }, { +aload(1) })
            }

            // then
            assertThat(frames).containsExactly(
                FullFrame(4, listOf(IntegerVariableInfo, obj("java/lang/String")), emptyList()),
                FullFrame(5, listOf(IntegerVariableInfo, obj("java/lang/String")), listOf(obj("java/lang/String"))),
            )
        }

        @Test
        fun `meets a reference and null as the reference, whichever arrives first`() {
            // given the reference on the path that gets there first this time
            val frames = derived("(ILjava/lang/String;)Ljava/lang/Object;") {
                choose({ +aload(1) }, { +aconst_null })
            }

            // then
            assertThat(frames.last())
                .isEqualTo(FullFrame(5, listOf(IntegerVariableInfo, obj("java/lang/String")), listOf(obj("java/lang/String"))))
        }

        @Test
        fun `meets two classes at the one the hierarchy names`() {
            // given a hierarchy that knows what a String and an Integer have in common
            val asked = mutableListOf<Pair<String, String>>()
            val hierarchy = object : ClassHierarchy {
                override fun isAssignable(from: String, to: String) = true
                override fun commonSuperclass(a: String, b: String) =
                    "java/io/Serializable".also { asked.add(a to b) }
            }

            // when
            val frames = derived("(I)Ljava/lang/Object;", hierarchy = hierarchy) {
                choose(
                    { +ldc(string("s")) },
                    { +iconst(1); invokestatic(clazz("java/lang/Integer"), "valueOf", "(I)Ljava/lang/Integer;") },
                )
            }

            // then
            assertThat(asked.flatMap { it.toList() }.toSet())
                .containsExactlyInAnyOrder("java/lang/String", "java/lang/Integer")
            assertThat(frames.last())
                .isEqualTo(FullFrame(6, listOf(IntegerVariableInfo), listOf(obj("java/io/Serializable"))))
        }

        @Test
        fun `meets two classes as Object when the hierarchy knows neither`() {
            // given
            val frames = derived("(I)Ljava/lang/Object;") {
                choose(
                    { +ldc(string("s")) },
                    { +iconst(1); invokestatic(clazz("java/lang/Integer"), "valueOf", "(I)Ljava/lang/Integer;") },
                )
            }

            // then
            assertThat(frames.last())
                .isEqualTo(FullFrame(6, listOf(IntegerVariableInfo), listOf(obj("java/lang/Object"))))
        }

        @Test
        fun `keeps an object fresh from new uninitialised across a jump`() {
            // given the object and its copy for the constructor both on the stack at the jump
            val frames = derived {
                +new(clazz("java/lang/Object"))
                +dup
                +iconst(0)
                val jump = +ifeq
                +nop
                val target = invokespecial(clazz("java/lang/Object"), "<init>", "()V") // 5
                link(jump, target)
                +`return`
            }

            // then both copies name the new that made them
            assertThat(frames).containsExactly(
                FullFrame(5, emptyList(), listOf(UninitializedVariableInfo(0), UninitializedVariableInfo(0))),
            )
        }

        @Test
        fun `initialises every copy of an object once its constructor has run`() {
            // given the copy left behind by dup, crossing a jump after the constructor
            val frames = derived {
                +new(clazz("java/lang/Object"))
                +dup
                invokespecial(clazz("java/lang/Object"), "<init>", "()V")
                +iconst(0)
                val jump = +ifeq
                +nop
                val target = +`return` // 6
                link(jump, target)
            }

            // then
            assertThat(frames).containsExactly(FullFrame(6, emptyList(), listOf(obj("java/lang/Object"))))
        }
    }

    @Nested
    inner class Layout {

        @Test
        fun `measures the first frame from the start of the code and each later one from the frame before`() {
            // given targets at bytes 8 and 9
            val written = frames(cp) {
                +iconst(0)
                val first = +ifeq
                +iconst(0)
                val second = +ifeq
                link(first, +nop)
                link(second, +`return`)
            }

            // then the second is one byte on, which is a delta of zero
            assertThat(written).containsExactly(
                FullFrame(8, emptyList(), emptyList()),
                FullFrame(0, emptyList(), emptyList()),
            )
        }

        @Test
        fun `writes one frame for a target however many jumps reach it`() {
            // given
            val written = frames(cp) {
                +iconst(0)
                val first = +ifeq
                +iconst(0)
                val second = +ifeq
                +`return`
                val target = +`return` // byte 9
                link(first, target); link(second, target)
            }

            // then
            assertThat(written).containsExactly(FullFrame(9, emptyList(), emptyList()))
        }

        @Test
        fun `points an uninitialised type at the byte offset of its new`() {
            // given a new at index 1 that a three byte sipush moves to byte 3
            val written = frames(cp) {
                +iconst(1000)
                +new(clazz("java/lang/Object"))
                +dup
                +iconst(0)
                val jump = +ifeq
                +nop
                link(jump, invokespecial(clazz("java/lang/Object"), "<init>", "()V")) // byte 12
                +`return`
            }

            // then
            assertThat(written).containsExactly(
                FullFrame(
                    12,
                    emptyList(),
                    listOf(IntegerVariableInfo, UninitializedVariableInfo(3), UninitializedVariableInfo(3)),
                ),
            )
        }
    }
}
