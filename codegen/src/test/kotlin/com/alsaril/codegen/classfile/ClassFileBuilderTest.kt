package com.alsaril.codegen.classfile

import com.alsaril.codegen.bytesOf
import com.alsaril.codegen.classfile.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.MethodAccessFlag.PUBLIC
import com.alsaril.codegen.classfile.MethodAccessFlag.STATIC
import com.alsaril.codegen.classfile.code.`return`
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
