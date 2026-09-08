package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.constantpool.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PointersTest {

    @Nested
    inner class Classes {

        @Test
        fun `registers the enclosing and parent classes in the pool`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            val self = builder.self()
            val parent = builder.parent()
            val other = builder.clazz("java/lang/String")

            // then each name is stored as a utf8 followed by the class entry pointing at it
            assertThat(listOf(self, parent, other))
                .containsExactly(ClassPointer(2), ClassPointer(4), ClassPointer(6))
            assertThat(cp.build().entries).containsExactly(
                ConstantUtf8Info(THIS_CLASS),
                ConstantClassInfo(nameIndex = 1),
                ConstantUtf8Info(PARENT_CLASS),
                ConstantClassInfo(nameIndex = 3),
                ConstantUtf8Info("java/lang/String"),
                ConstantClassInfo(nameIndex = 5),
            )
        }

        @Test
        fun `reuses one pool entry for a repeated class`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            val first = builder.clazz("A")
            val second = builder.clazz("A")

            // then
            assertThat(second).isEqualTo(first)
            assertThat(cp.build().entries).hasSize(2)
        }

        @Test
        fun `points self and parent at their own entries`() {
            // given
            val builder = builder()

            // then
            assertThat(builder.self()).isNotEqualTo(builder.parent())
        }
    }

    @Nested
    inner class Constants {

        @Test
        fun `registers an integer constant`() {
            // given
            val cp = UpdatableConstantPool()

            // when
            val pointer = builder(cp).int(42)

            // then
            assertThat(pointer).isEqualTo(DataPointer(1))
            assertThat(cp.build().entries).containsExactly(ConstantIntegerInfo(42))
        }

        @Test
        fun `registers a float constant`() {
            // given
            val cp = UpdatableConstantPool()

            // when
            val pointer = builder(cp).float(1.5f)

            // then
            assertThat(pointer).isEqualTo(DataPointer(1))
            assertThat(cp.build().entries).containsExactly(ConstantFloatInfo(1.5f))
        }

        @Test
        fun `registers a string constant behind its utf8`() {
            // given
            val cp = UpdatableConstantPool()

            // when
            val pointer = builder(cp).string("boom")

            // then the pointer addresses the string entry, not the utf8 it wraps
            assertThat(pointer).isEqualTo(DataPointer(2))
            assertThat(cp.build().entries).containsExactly(
                ConstantUtf8Info("boom"),
                ConstantStringInfo(valueIndex = 1),
            )
        }

        @Test
        fun `reuses one pool entry for a repeated constant`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            val int = builder.int(1)
            val string = builder.string("s")

            // then
            assertThat(builder.int(1)).isEqualTo(int)
            assertThat(builder.string("s")).isEqualTo(string)
            assertThat(cp.build().entries).hasSize(3)
        }

        @Test
        fun `keeps an integer and a string apart`() {
            // given
            val builder = builder()

            // then
            assertThat(builder.int(1)).isNotEqualTo(builder.string("1"))
        }

        @Test
        fun `keeps an integer and a float of the same value apart`() {
            // given
            val builder = builder()

            // then
            assertThat(builder.int(1)).isNotEqualTo(builder.float(1.0f))
        }
    }
}
