package com.alsaril.codegen.code

import com.alsaril.codegen.classfile.attributes.BootstrapMethod
import com.alsaril.codegen.constantpool.*
import com.alsaril.codegen.verification.PrimitiveType.FLOAT
import com.alsaril.codegen.verification.PrimitiveType.INTEGER
import com.alsaril.codegen.verification.ReferenceType
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

    @Nested
    inner class Constants {

        @Test
        fun `registers an integer constant`() {
            // given
            val cp = UpdatableConstantPool()

            // when
            val pointer = builder(cp).int(42)

            // then
            assertThat(pointer).isEqualTo(DataPointer(1, INTEGER))
            assertThat(cp.build().entries).containsExactly(ConstantIntegerInfo(42))
        }

        @Test
        fun `registers a float constant`() {
            // given
            val cp = UpdatableConstantPool()

            // when
            val pointer = builder(cp).float(1.5f)

            // then
            assertThat(pointer).isEqualTo(DataPointer(1, FLOAT))
            assertThat(cp.build().entries).containsExactly(ConstantFloatInfo(1.5f))
        }

        @Test
        fun `registers a string constant behind its utf8`() {
            // given
            val cp = UpdatableConstantPool()

            // when
            val pointer = builder(cp).string("boom")

            // then the pointer addresses the string entry, not the utf8 it wraps
            assertThat(pointer).isEqualTo(DataPointer(2, ReferenceType("java/lang/String")))
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

    @Nested
    inner class DynamicConstants {

        private val bootstrap = "(${ClassFileBuilder.BOOTSTRAP_PREFIX})I"

        @Test
        fun `registers the bootstrap handle, then the constant typed as the bootstrap method returns`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)
            val owner = builder.clazz("B")

            // when
            val pointer = builder.constantDynamic(owner, "boot", bootstrap)

            // then the method ref and a static handle to it, and the constant behind its name-and-type
            assertThat(pointer).isEqualTo(DataPointer(11, INTEGER))
            assertThat(cp.build().entries).containsExactly(
                ConstantUtf8Info("B"),
                ConstantClassInfo(nameIndex = 1),
                ConstantUtf8Info("boot"),
                ConstantUtf8Info(bootstrap),
                ConstantNameAndTypeInfo(nameIndex = 3, descriptorIndex = 4),
                ConstantMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                ConstantMethodHandleInfo(ConstantMethodHandleInfo.ReferenceKind.INVOKE_STATIC, referenceIndex = 6),
                ConstantUtf8Info("_"),
                ConstantUtf8Info("I"),
                ConstantNameAndTypeInfo(nameIndex = 8, descriptorIndex = 9),
                ConstantDynamicInfo(bootstrapMethodIndex = 0, nameAndTypeIndex = 10),
            )
            assertThat(builder.bootstrapMethods.methods()).containsExactly(BootstrapMethod(7, emptyList()))
        }

        @Test
        fun `types the constant as the class the bootstrap method returns`() {
            // given
            val builder = builder()

            // when
            val pointer = builder.constantDynamic(
                builder.clazz("B"), "boot", "(${ClassFileBuilder.BOOTSTRAP_PREFIX})Ljava/lang/String;",
            )

            // then
            assertThat(pointer.type).isEqualTo(ReferenceType("java/lang/String"))
        }

        @Test
        fun `passes the arguments to the bootstrap method as their pool indices`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)
            val first = builder.int(1)
            val second = builder.string("s")

            // when
            builder.constantDynamic(builder.clazz("B"), "boot", "(${ClassFileBuilder.BOOTSTRAP_PREFIX}ILjava/lang/String;)I", first, second)

            // then
            assertThat(builder.bootstrapMethods.methods().single().bootstrapArguments)
                .containsExactly(first.index, second.index)
        }

        @Test
        fun `reuses one constant and one bootstrap method for a repeated constant`() {
            // given
            val builder = builder()
            val owner = builder.clazz("B")

            // when
            val first = builder.constantDynamic(owner, "boot", bootstrap, builder.int(1))
            val second = builder.constantDynamic(owner, "boot", bootstrap, builder.int(1))

            // then
            assertThat(second).isEqualTo(first)
            assertThat(builder.bootstrapMethods.methods()).hasSize(1)
        }

        @Test
        fun `keeps constants with different arguments apart`() {
            // given
            val builder = builder()
            val owner = builder.clazz("B")

            // when
            val one = builder.constantDynamic(owner, "boot", bootstrap, builder.int(1))
            val two = builder.constantDynamic(owner, "boot", bootstrap, builder.int(2))

            // then
            assertThat(two.index).isNotEqualTo(one.index)
            assertThat(builder.bootstrapMethods.methods()).hasSize(2)
        }

        @Test
        fun `takes another dynamic constant as an argument`() {
            // given
            val builder = builder()
            val owner = builder.clazz("B")
            val inner = builder.constantDynamic(owner, "inner", bootstrap)

            // when
            builder.constantDynamic(owner, "outer", "(${ClassFileBuilder.BOOTSTRAP_PREFIX}I)I", inner)

            // then
            assertThat(builder.bootstrapMethods.methods().last().bootstrapArguments).containsExactly(inner.index)
        }
    }
}
