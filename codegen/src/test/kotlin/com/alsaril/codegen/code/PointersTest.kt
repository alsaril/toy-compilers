package com.alsaril.codegen.code

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
                .containsExactly(ClassPointer(2, THIS_CLASS), ClassPointer(4, PARENT_CLASS), ClassPointer(6, "java/lang/String"))
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
}
