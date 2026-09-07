package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.bytesOf
import com.alsaril.codegen.constantpool.ConstantClassInfo
import com.alsaril.codegen.constantpool.ConstantFieldRefInfo
import com.alsaril.codegen.constantpool.ConstantMethodRefInfo
import com.alsaril.codegen.constantpool.ConstantNameAndTypeInfo
import com.alsaril.codegen.constantpool.ConstantUtf8Info
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class InvocationsTest {

    @Nested
    inner class Descriptors {

        @Test
        fun `registers a method ref in the constant pool`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            val descriptor = builder.method(builder.clazz("A"), "f", "()V")

            // then
            assertThat(descriptor).isEqualTo(MethodDescriptor(6))
            assertThat(cp.build().entries).containsExactly(
                ConstantUtf8Info("A"),
                ConstantClassInfo(nameIndex = 1),
                ConstantUtf8Info("f"),
                ConstantUtf8Info("()V"),
                ConstantNameAndTypeInfo(nameIndex = 3, descriptorIndex = 4),
                ConstantMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
            )
        }

        @Test
        fun `registers a field ref in the constant pool`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            val descriptor = builder.field(builder.clazz("A"), "x", "I")

            // then
            assertThat(descriptor).isEqualTo(FieldDescriptor(6))
            assertThat(cp.build().entries).last()
                .isEqualTo(ConstantFieldRefInfo(classNameIndex = 2, nameAndTypeIndex = 5))
        }

        @Test
        fun `reuses one pool entry for a repeated lookup`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            val first = builder.method(builder.clazz("A"), "f", "()V")
            val second = builder.method(builder.clazz("A"), "f", "()V")

            // then
            assertThat(second).isEqualTo(first)
            assertThat(cp.build().entries).hasSize(6)
        }
    }

    @Nested
    inner class Instructions {

        @Test
        fun `writes the invoke family with the ref index`() {
            assertThat(bytecode { invokevirtual(MethodDescriptor(0x0102)) })
                .containsExactly(*bytesOf(0xB6, 0x01, 0x02))
            assertThat(bytecode { invokespecial(MethodDescriptor(1)) })
                .containsExactly(*bytesOf(0xB7, 0x00, 0x01))
            assertThat(bytecode { invokestatic(MethodDescriptor(1)) })
                .containsExactly(*bytesOf(0xB8, 0x00, 0x01))
        }

        @Test
        fun `writes invokeinterface with its argument count and trailing zero`() {
            assertThat(bytecode { invokeinterface(MethodDescriptor(1), count = 2) })
                .containsExactly(*bytesOf(0xB9, 0x00, 0x01, 0x02, 0x00))
        }

        @Test
        fun `writes getstatic with the field index`() {
            assertThat(bytecode { getstatic(FieldDescriptor(3)) })
                .containsExactly(*bytesOf(0xB2, 0x00, 0x03))
        }

        @Test
        fun `writes new with the class index`() {
            assertThat(bytecode { new(ClassPointer(4)) }).containsExactly(*bytesOf(0xBB, 0x00, 0x04))
        }

        @Test
        fun `writes newarray with the type code`() {
            assertThat(bytecode { newarray(ArrayType.BYTE) })
                .containsExactly(*bytesOf(0xBC, 0x08))
            assertThat(bytecode { newarray(ArrayType.INT) })
                .containsExactly(*bytesOf(0xBC, 0x0A))
        }

        @Test
        fun `expands construct into new, dup and the constructor call`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            builder.construct(builder.clazz("A"), "<init>", "()V")

            // then class A is index 2 and the method ref lands at 6
            assertThat(builder.build().bytecode()).containsExactly(
                *bytesOf(
                    0xBB, 0x00, 0x02,  // new A
                    0x59,              // dup
                    0xB7, 0x00, 0x06,  // invokespecial <init>
                ),
            )
        }
    }
}
