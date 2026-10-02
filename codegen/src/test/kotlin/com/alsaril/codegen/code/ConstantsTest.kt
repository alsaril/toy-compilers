package com.alsaril.codegen.code

import com.alsaril.codegen.constantpool.*
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.*
import com.alsaril.codegen.verification.PrimitiveType.*
import com.alsaril.codegen.verification.ReferenceType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** The constants ldc and ldc2_w load, which a bootstrap method can take as arguments too. */
class ConstantsTest {

    @Nested
    inner class Values {

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
        fun `registers a long constant across two slots`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            val pointer = builder.long(1L shl 40)
            val next = builder.int(1)

            // then the entry after it starts two slots on
            assertThat(pointer).isEqualTo(DataPointer(1, LONG))
            assertThat(next.index).isEqualTo(3)
            assertThat(cp.build().entries).containsExactly(ConstantLongInfo(1L shl 40), ConstantIntegerInfo(1))
        }

        @Test
        fun `registers a double constant across two slots`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            val pointer = builder.double(2.5)
            val next = builder.int(1)

            // then
            assertThat(pointer).isEqualTo(DataPointer(1, DOUBLE))
            assertThat(next.index).isEqualTo(3)
            assertThat(cp.build().entries).containsExactly(ConstantDoubleInfo(2.5), ConstantIntegerInfo(1))
        }

        @Test
        fun `registers a method type behind the utf8 of its descriptor`() {
            // given
            val cp = UpdatableConstantPool()

            // when
            val pointer = builder(cp).methodType("(I)V")

            // then
            assertThat(pointer).isEqualTo(DataPointer(2, ReferenceType("java/lang/invoke/MethodType")))
            assertThat(cp.build().entries).containsExactly(
                ConstantUtf8Info("(I)V"),
                ConstantMethodTypeInfo(descriptorIndex = 1),
            )
        }

        @Test
        fun `reuses one pool entry for a repeated method type`() {
            // given
            val builder = builder()

            // then
            assertThat(builder.methodType("(I)V")).isEqualTo(builder.methodType("(I)V"))
            assertThat(builder.methodType("(J)V")).isNotEqualTo(builder.methodType("(I)V"))
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
    inner class MethodHandles {

        private val handle = ReferenceType("java/lang/invoke/MethodHandle")

        @Test
        fun `register a ref and a handle of the given kind to it`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            val pointer = builder.methodHandle(INVOKE_STATIC, builder.clazz("A"), "f", "(I)V")

            // then
            assertThat(pointer).isEqualTo(DataPointer(7, handle))
            assertThat(cp.build().entries).containsExactly(
                ConstantUtf8Info("A"),
                ConstantClassInfo(nameIndex = 1),
                ConstantUtf8Info("f"),
                ConstantUtf8Info("(I)V"),
                ConstantNameAndTypeInfo(nameIndex = 3, descriptorIndex = 4),
                ConstantMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                ConstantMethodHandleInfo(INVOKE_STATIC, referenceIndex = 6),
            )
        }

        @Test
        fun `refer to a field for the field kinds`() {
            listOf(GET_FIELD, GET_STATIC, PUT_FIELD, PUT_STATIC).forEach { kind ->
                // given
                val cp = UpdatableConstantPool()
                val builder = builder(cp)

                // when
                builder.methodHandle(kind, builder.clazz("A"), "x", "I")

                // then
                assertThat(cp.build().entries.takeLast(2)).containsExactly(
                    ConstantFieldRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                    ConstantMethodHandleInfo(kind, referenceIndex = 6),
                )
            }
        }

        @Test
        fun `refer to a method for the method kinds`() {
            listOf(INVOKE_VIRTUAL, INVOKE_STATIC, INVOKE_SPECIAL).forEach { kind ->
                // given
                val cp = UpdatableConstantPool()
                val builder = builder(cp)

                // when
                builder.methodHandle(kind, builder.clazz("A"), "f", "()V")

                // then
                assertThat(cp.build().entries.takeLast(2)).containsExactly(
                    ConstantMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                    ConstantMethodHandleInfo(kind, referenceIndex = 6),
                )
            }
        }

        @Test
        fun `refer to an interface method for invokeinterface`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            builder.methodHandle(INVOKE_INTERFACE, builder.clazz("A"), "f", "()V")

            // then
            assertThat(cp.build().entries.takeLast(2)).containsExactly(
                ConstantInterfaceMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                ConstantMethodHandleInfo(INVOKE_INTERFACE, referenceIndex = 6),
            )
        }

        @Test
        fun `refer to an interface method for a static or special handle on an interface`() {
            listOf(INVOKE_STATIC, INVOKE_SPECIAL).forEach { kind ->
                // given
                val cp = UpdatableConstantPool()
                val builder = builder(cp)

                // when
                builder.methodHandle(kind, builder.clazz("A"), "f", "()V", onInterface = true)

                // then
                assertThat(cp.build().entries.takeLast(2)).containsExactly(
                    ConstantInterfaceMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                    ConstantMethodHandleInfo(kind, referenceIndex = 6),
                )
            }
        }

        @Test
        fun `keep handles to a method of a class and of an interface apart`() {
            // given
            val builder = builder()
            val owner = builder.clazz("A")

            // when
            val onClass = builder.methodHandle(INVOKE_STATIC, owner, "f", "()V")
            val onInterface = builder.methodHandle(INVOKE_STATIC, owner, "f", "()V", onInterface = true)

            // then
            assertThat(onInterface).isNotEqualTo(onClass)
        }

        @Test
        fun `refuse an interface for any kind but static, special and interface`() {
            listOf(GET_FIELD, GET_STATIC, PUT_FIELD, PUT_STATIC, INVOKE_VIRTUAL).forEach { kind ->
                assertThatIllegalArgumentException()
                    .isThrownBy { builder().run { methodHandle(kind, clazz("A"), "f", "()V", onInterface = true) } }
                    .withMessage("a $kind handle cannot refer to a method of an interface, only a static, a special or an interface one can")
            }
            assertThatIllegalArgumentException()
                .isThrownBy { builder().run { methodHandle(NEW_INVOKE_SPECIAL, clazz("A"), "<init>", "()V", onInterface = true) } }
                .withMessage("a NEW_INVOKE_SPECIAL handle cannot refer to a method of an interface, only a static, a special or an interface one can")
        }

        @Test
        fun `refer to an interface method for invokeinterface whether it is asked for or not`() {
            // given
            val builder = builder()
            val owner = builder.clazz("A")

            // then
            assertThat(builder.methodHandle(INVOKE_INTERFACE, owner, "f", "()V", onInterface = true))
                .isEqualTo(builder.methodHandle(INVOKE_INTERFACE, owner, "f", "()V"))
        }

        @Test
        fun `refer to the constructor for a new object`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            builder.methodHandle(NEW_INVOKE_SPECIAL, builder.clazz("A"), "<init>", "()V")

            // then
            assertThat(cp.build().entries.takeLast(2)).containsExactly(
                ConstantMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                ConstantMethodHandleInfo(NEW_INVOKE_SPECIAL, referenceIndex = 6),
            )
        }

        @Test
        fun `refuse a constructor for any kind but a new object`() {
            assertThatIllegalArgumentException()
                .isThrownBy { builder().run { methodHandle(INVOKE_SPECIAL, clazz("A"), "<init>", "()V") } }
                .withMessage("a INVOKE_SPECIAL handle to <init>: only NEW_INVOKE_SPECIAL refers to <init>, and it refers to nothing else")
        }

        @Test
        fun `refuse a new object from anything but a constructor`() {
            assertThatIllegalArgumentException()
                .isThrownBy { builder().run { methodHandle(NEW_INVOKE_SPECIAL, clazz("A"), "create", "()V") } }
                .withMessage("a NEW_INVOKE_SPECIAL handle to create: only NEW_INVOKE_SPECIAL refers to <init>, and it refers to nothing else")
        }

        @Test
        fun `reuse one pool entry for a repeated handle`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)
            val first = builder.methodHandle(INVOKE_STATIC, builder.clazz("A"), "f", "()V")

            // when
            val second = builder.methodHandle(INVOKE_STATIC, builder.clazz("A"), "f", "()V")

            // then
            assertThat(second).isEqualTo(first)
            assertThat(cp.build().entries).hasSize(7)
        }
    }
}
