package com.alsaril.codegen.classfile

import com.alsaril.codegen.bytesOf
import com.alsaril.codegen.classfile.code.CodeBuilder
import com.alsaril.codegen.classfile.code.iadd
import com.alsaril.codegen.classfile.code.iconst
import com.alsaril.codegen.classfile.code.ireturn
import com.alsaril.codegen.classfile.code.istore
import com.alsaril.codegen.methodLimits
import com.alsaril.codegen.classfile.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.MethodAccessFlag.PUBLIC
import com.alsaril.codegen.classfile.MethodAccessFlag.STATIC
import com.alsaril.codegen.classfile.code.clazz
import com.alsaril.codegen.classfile.code.parent
import com.alsaril.codegen.classfile.code.self
import com.alsaril.codegen.classfile.code.`return`
import com.alsaril.codegen.classfile.code.nop
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ClassFileBuilderTest {

    @Test
    fun `returns the class name alongside the bytes`() {
        // when
        val (name, bytes) = classFile("Empty", "java/lang/Object").build()

        // then
        assertThat(name).isEqualTo("Empty")
        assertThat(bytes).isNotEmpty()
    }

    @Test
    fun `starts with the magic number and the java 8 version`() {
        // when
        val (_, bytes) = classFile("Header", "java/lang/Object").build()

        // then
        assertThat(bytes).startsWith(*bytesOf(0xCA, 0xFE, 0xBA, 0xBE, 0x00, 0x00, 0x00, 0x34))
    }

    @Test
    fun `ends with an empty class attribute list`() {
        // when
        val (_, bytes) = classFile("Tail", "java/lang/Object").build()

        // then
        assertThat(bytes).endsWith(*bytesOf(0x00, 0x00))
    }

    @Test
    fun `accepts a method declared without access flags`() {
        // a package private method folds to no flags at all
        assertThatNoException().isThrownBy {
            classFile("NoFlags", "java/lang/Object")
                .method("f", "()V") { `return`() }
                .build()
        }
    }

    @Test
    fun `accepts several flags on one method`() {
        assertThatNoException().isThrownBy {
            classFile("ManyFlags", "java/lang/Object")
                .method("f", "()V", PUBLIC, STATIC) { `return`() }
                .build()
        }
    }

    @Test
    fun `hands out code builders that share one constant pool`() {
        // given
        val builder = classFile("Shared", "java/lang/Object")

        // when the same class is registered through two of them
        val first = builder.newCodeBuilder().clazz("A")
        val second = builder.newCodeBuilder().clazz("A")

        // then it landed on one entry, which is what lets fragments be built apart and
        // then spliced into a method of the same class
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `hands out code builders that know the class they belong to`() {
        val builder = classFile("Shared", "java/lang/Object")

        assertThat(builder.newCodeBuilder().self())
            .isEqualTo(builder.newCodeBuilder().self())
            .isNotEqualTo(builder.newCodeBuilder().parent())
    }

    @Test
    fun `emits a standalone fragment without adding a method`() {
        // given
        val builder = classFile("Fragments", "java/lang/Object")

        // when
        val fragment = builder.emitFragment { `return`() }

        // then the fragment carries the code, and no method was declared for it
        assertThat(fragment.bytecode()).containsExactly(*bytesOf(0xB1))
        assertThat(builder.build().second).isEqualTo(
            classFile("Fragments", "java/lang/Object").build().second
        )
    }

    @Nested
    inner class MaxStack {

        private fun stack(descriptor: String, vararg flags: MethodAccessFlag, body: CodeBuilder.() -> Unit) =
            classFile("Stack", "java/lang/Object")
                .method("f", descriptor, *flags, codeBuilder = body)
                .build().second
                .let { methodLimits(it).single { method -> method.name == "f" }.maxStack }

        @Test
        fun `asks for no stack when the body pushes nothing`() {
            assertThat(stack("()V", STATIC) { nop(); `return`() }).isZero()
        }

        @Test
        fun `takes the depth the body reaches`() {
            assertThat(stack("()I", STATIC) { iconst(1); iconst(1); iadd(); ireturn() })
                .isEqualTo(2)
        }

        @Test
        fun `takes the deepest point rather than the last`() {
            // three deep in the middle, one deep by the time it returns
            assertThat(stack("()I", STATIC) { iconst(1); iconst(1); iconst(1); iadd(); iadd(); ireturn() })
                .isEqualTo(3)
        }

        // both overloads derive the depth, so a caller assembling fragments itself is
        // treated the same as one handing over a body
        @Test
        fun `takes the depth of a fragment handed over as the body`() {
            // given
            val builder = classFile("Stack", "java/lang/Object")
            val fragment = builder.emitFragment { iconst(1); iconst(1); iadd(); ireturn() }

            // when
            val bytes = builder.method("f", "()I", fragment, STATIC).build().second

            // then
            assertThat(methodLimits(bytes).single { it.name == "f" }.maxStack).isEqualTo(2)
        }
    }

    @Nested
    inner class MaxLocals {

        private fun locals(descriptor: String, vararg flags: MethodAccessFlag, body: CodeBuilder.() -> Unit = { nop(); `return`() }) =
            classFile("Locals", "java/lang/Object")
                .method("f", descriptor, *flags, codeBuilder = body)
                .build().second
                .let { methodLimits(it).single { method -> method.name == "f" }.maxLocals }

        @Test
        fun `counts the arguments a static method declares`() {
            assertThat(locals("()V", STATIC)).isZero()
            assertThat(locals("(I)V", STATIC)).isOne()
            assertThat(locals("(II)V", STATIC)).isEqualTo(2)
        }

        @Test
        fun `counts a long and a double argument as two slots each`() {
            assertThat(locals("(J)V", STATIC)).isEqualTo(2)
            assertThat(locals("(JD)V", STATIC)).isEqualTo(4)
            assertThat(locals("(IJ)V", STATIC)).isEqualTo(3)
        }

        @Test
        fun `counts a reference or an array argument as one`() {
            assertThat(locals("(Ljava/lang/String;)V", STATIC)).isOne()
            assertThat(locals("([J)V", STATIC)).isOne()
        }

        @Test
        fun `counts this among the locals of an instance method`() {
            assertThat(locals("()V", PUBLIC)).isOne()
            assertThat(locals("(I)V", PUBLIC)).isEqualTo(2)
            assertThat(locals("(J)V", PUBLIC)).isEqualTo(3)
        }

        @Test
        fun `takes the slot the body reaches when it runs past the arguments`() {
            // slot 5 is the sixth, so the method needs six
            assertThat(locals("(I)V", STATIC) { iconst(0); istore(5); `return`() }).isEqualTo(6)
        }

        @Test
        fun `does not count this twice when the body reaches past the arguments`() {
            // a body's slots are already absolute - an instance method addresses this as
            // slot 0 - so the room for this belongs to the descriptor's count alone
            assertThat(locals("()V", PUBLIC) { iconst(0); istore(3); `return`() }).isEqualTo(4)
            assertThat(locals("(I)V", PUBLIC) { iconst(0); istore(3); `return`() }).isEqualTo(4)
        }

        @Test
        fun `keeps the arguments when the body stays inside them`() {
            assertThat(locals("(JD)V", STATIC) { iconst(0); istore(0); `return`() }).isEqualTo(4)
        }

        @Test
        fun `counts the slot a body reaches as an index, so one local needs one slot`() {
            assertThat(locals("()V", STATIC) { iconst(0); istore(0); `return`() }).isOne()
        }

        @Test
        fun `takes the slots of a fragment spliced into the body`() {
            val builder = classFile("Locals", "java/lang/Object")
            val piece = builder.emitFragment { iconst(0); istore(4); `return`() }

            val bytes = builder
                .method("f", "()V", piece, STATIC)
                .build().second

            assertThat(methodLimits(bytes).single { it.name == "f" }.maxLocals).isEqualTo(5)
        }
    }

    @Test
    fun `grows the output as methods are added`() {
        // given
        val bare = classFile("Bare", "java/lang/Object").build().second
        val withMethod = classFile("WithMethod", "java/lang/Object")
            .method("f", "()V", PUBLIC) { `return`() }
            .build().second

        // then
        assertThat(withMethod.size).isGreaterThan(bare.size)
    }
}
