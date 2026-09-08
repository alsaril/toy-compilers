package com.alsaril.codegen.classfile

import com.alsaril.codegen.bytesOf
import com.alsaril.codegen.classfile.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.MethodAccessFlag.PUBLIC
import com.alsaril.codegen.classfile.MethodAccessFlag.STATIC
import com.alsaril.codegen.classfile.code.`return`
import com.alsaril.codegen.classfile.code.nop
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
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
                .method("f", "()V", maxStack = 0, maxLocals = 0) { `return`() }
                .build()
        }
    }

    @Test
    fun `accepts several flags on one method`() {
        assertThatNoException().isThrownBy {
            classFile("ManyFlags", "java/lang/Object")
                .method("f", "()V", maxStack = 0, maxLocals = 0, PUBLIC, STATIC) { `return`() }
                .build()
        }
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

    @Test
    fun `raises the declared stack depth to what the body needs`() {
        // given a body asking for more stack than the method declared
        val declared = classFile("Stack", "java/lang/Object")
            .method("f", "()V", maxStack = 0, maxLocals = 0, PUBLIC) { maxStack(3); nop(); `return`() }
            .build().second

        // when the same body declares the depth up front instead
        val expected = classFile("Stack", "java/lang/Object")
            .method("f", "()V", maxStack = 3, maxLocals = 0, PUBLIC) { maxStack(3); nop(); `return`() }
            .build().second

        // then
        assertThat(declared).isEqualTo(expected)
    }

    @Test
    fun `keeps the declared stack depth when the body needs less`() {
        // given
        val withBody = classFile("Stack", "java/lang/Object")
            .method("f", "()V", maxStack = 5, maxLocals = 0, PUBLIC) { maxStack(3); nop(); `return`() }
            .build().second

        // when the body asks for nothing at all
        val withoutBody = classFile("Stack", "java/lang/Object")
            .method("f", "()V", maxStack = 5, maxLocals = 0, PUBLIC) { nop(); `return`() }
            .build().second

        // then the larger declared value survives
        assertThat(withBody).isEqualTo(withoutBody)
    }

    // both overloads raise the declared depth, so a caller assembling fragments itself is
    // treated the same as one handing over a body
    @Test
    fun `raises the declared stack depth when handed a fragment`() {
        // given a fragment needing more stack than the method declares
        val builder = classFile("Stack", "java/lang/Object")
        val fragment = builder.emitFragment { maxStack(3); nop(); `return`() }
        val declared = builder
            .method("f", "()V", fragment, maxStack = 0, maxLocals = 0, PUBLIC)
            .build().second

        // when the same fragment is given the depth up front
        val expected = classFile("Stack", "java/lang/Object")
            .let {
                it.method(
                    "f",
                    "()V",
                    it.emitFragment { maxStack(3); nop(); `return`() },
                    maxStack = 3,
                    maxLocals = 0,
                    PUBLIC,
                )
            }
            .build().second

        // then
        assertThat(declared).isEqualTo(expected)
    }

    @Test
    fun `grows the output as methods are added`() {
        // given
        val bare = classFile("Bare", "java/lang/Object").build().second
        val withMethod = classFile("WithMethod", "java/lang/Object")
            .method("f", "()V", maxStack = 0, maxLocals = 0, PUBLIC) { `return`() }
            .build().second

        // then
        assertThat(withMethod.size).isGreaterThan(bare.size)
    }
}
