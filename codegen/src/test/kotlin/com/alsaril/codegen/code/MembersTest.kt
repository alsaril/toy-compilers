package com.alsaril.codegen.code

import com.alsaril.codegen.bytesOf
import com.alsaril.codegen.classfile.PrimitiveType.*
import com.alsaril.codegen.constantpool.ConstantClassInfo
import com.alsaril.codegen.constantpool.ConstantFieldRefInfo
import com.alsaril.codegen.constantpool.ConstantInterfaceMethodRefInfo
import com.alsaril.codegen.constantpool.ConstantMethodRefInfo
import com.alsaril.codegen.constantpool.ConstantNameAndTypeInfo
import com.alsaril.codegen.constantpool.ConstantUtf8Info
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.alsaril.codegen.instruction.*
import com.alsaril.codegen.constantpool.ClassPointer
import com.alsaril.codegen.constantpool.FieldDescriptor
import com.alsaril.codegen.constantpool.MethodDescriptor

class MembersTest {

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
            assertThat(descriptor).isEqualTo(MethodDescriptor(6, argSlots = 1, returnSlots = 0))
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
        fun `registers an interface method ref in the constant pool`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            val descriptor = builder.imethod(builder.clazz("A"), "f", "()V")

            // then
            assertThat(descriptor).isEqualTo(MethodDescriptor(6, argSlots = 1, returnSlots = 0))
            assertThat(cp.build().entries).last()
                .isEqualTo(ConstantInterfaceMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5))
        }

        @Test
        fun `keeps a method and an interface method of the same name apart`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)
            val clazz = builder.clazz("A")

            // when
            val method = builder.method(clazz, "f", "()V")
            val imethod = builder.imethod(clazz, "f", "()V")

            // then the two refs differ while sharing one name-and-type
            assertThat(imethod).isNotEqualTo(method)
            assertThat(cp.build().entries).endsWith(
                ConstantMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                ConstantInterfaceMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
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
            assertThat(descriptor).isEqualTo(FieldDescriptor(6, slots = 1))
            assertThat(cp.build().entries).last()
                .isEqualTo(ConstantFieldRefInfo(classNameIndex = 2, nameAndTypeIndex = 5))
        }

        @Test
        fun `counts the receiver among the operands of an instance method`() {
            // given
            val builder = builder()
            val a = builder.clazz("A")

            // then the receiver is on the stack under the arguments, so it is an operand too
            assertThat(builder.method(a, "f", "()V").argSlots).isOne()
            assertThat(builder.method(a, "f", "(I)V").argSlots).isEqualTo(2)
            assertThat(builder.method(a, "f", "(JD)V").argSlots).isEqualTo(5)
        }

        @Test
        fun `counts only the arguments of a static method`() {
            // given
            val builder = builder()
            val a = builder.clazz("A")

            // then there is no receiver to take off the stack
            assertThat(builder.smethod(a, "f", "()V").argSlots).isZero()
            assertThat(builder.smethod(a, "f", "(I)V").argSlots).isOne()
            assertThat(builder.smethod(a, "f", "(JD)V").argSlots).isEqualTo(4)
        }

        @Test
        fun `counts the receiver of an interface method, which invokeinterface writes as its count`() {
            // given
            val builder = builder()
            val a = builder.clazz("A")

            // then
            assertThat(builder.imethod(a, "f", "()V").argSlots).isOne()
            assertThat(builder.imethod(a, "f", "(Ljava/lang/Object;)I").argSlots).isEqualTo(2)
            assertThat(builder.imethod(a, "f", "(JD)V").argSlots).isEqualTo(5)
        }

        @Test
        fun `takes the slots a call leaves behind from the return type`() {
            // given
            val builder = builder()
            val a = builder.clazz("A")

            // then
            assertThat(builder.method(a, "f", "()V").returnSlots).isZero()
            assertThat(builder.method(a, "f", "()I").returnSlots).isOne()
            assertThat(builder.method(a, "f", "()Ljava/lang/String;").returnSlots).isOne()
            assertThat(builder.method(a, "f", "()J").returnSlots).isEqualTo(2)
            assertThat(builder.method(a, "f", "()D").returnSlots).isEqualTo(2)
        }

        @Test
        fun `counts the slots a field type occupies`() {
            // given
            val builder = builder()
            val a = builder.clazz("A")

            // then a long or a double is two slots wide on the stack, everything else one
            assertThat(builder.field(a, "x", "I").slots).isOne()
            assertThat(builder.field(a, "x", "Ljava/lang/String;").slots).isOne()
            assertThat(builder.field(a, "x", "[J").slots).isOne()
            assertThat(builder.field(a, "x", "J").slots).isEqualTo(2)
            assertThat(builder.field(a, "x", "D").slots).isEqualTo(2)
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
            assertThat(bytecode { +invokevirtual(MethodDescriptor(0x0102, argSlots = 0, returnSlots = 0)) })
                .containsExactly(*bytesOf(0xB6, 0x01, 0x02))
            assertThat(bytecode { +invokespecial(MethodDescriptor(1, argSlots = 0, returnSlots = 0)) })
                .containsExactly(*bytesOf(0xB7, 0x00, 0x01))
            assertThat(bytecode { +invokestatic(MethodDescriptor(1, argSlots = 0, returnSlots = 0)) })
                .containsExactly(*bytesOf(0xB8, 0x00, 0x01))
        }

        @Test
        fun `writes invokeinterface with its argument count and trailing zero`() {
            assertThat(bytecode { +invokeinterface(MethodDescriptor(1, argSlots = 2, returnSlots = 0)) })
                .containsExactly(*bytesOf(0xB9, 0x00, 0x01, 0x02, 0x00))
        }

        @Test
        fun `writes getstatic with the field index`() {
            assertThat(bytecode { +getstatic(FieldDescriptor(3, slots = 1)) })
                .containsExactly(*bytesOf(0xB2, 0x00, 0x03))
        }

        @Test
        fun `writes new with the class index`() {
            assertThat(bytecode { +new(ClassPointer(4)) }).containsExactly(*bytesOf(0xBB, 0x00, 0x04))
        }

        @Test
        fun `writes checkcast with the class index`() {
            assertThat(bytecode { +checkcast(ClassPointer(4)) })
                .containsExactly(*bytesOf(0xC0, 0x00, 0x04))
        }

        @Test
        fun `writes instanceof with the class index`() {
            assertThat(bytecode { +instanceof(ClassPointer(4)) })
                .containsExactly(*bytesOf(0xC1, 0x00, 0x04))
        }

        @Test
        fun `writes newarray with the atype of its element`() {
            // the codes JVMS 6.5 lists for newarray, T_BOOLEAN through T_LONG
            assertThat(bytecode { +newarray(BOOLEAN) }).containsExactly(*bytesOf(0xBC, 0x04))
            assertThat(bytecode { +newarray(CHAR) }).containsExactly(*bytesOf(0xBC, 0x05))
            assertThat(bytecode { +newarray(FLOAT) }).containsExactly(*bytesOf(0xBC, 0x06))
            assertThat(bytecode { +newarray(DOUBLE) }).containsExactly(*bytesOf(0xBC, 0x07))
            assertThat(bytecode { +newarray(BYTE) }).containsExactly(*bytesOf(0xBC, 0x08))
            assertThat(bytecode { +newarray(SHORT) }).containsExactly(*bytesOf(0xBC, 0x09))
            assertThat(bytecode { +newarray(INT) }).containsExactly(*bytesOf(0xBC, 0x0A))
            assertThat(bytecode { +newarray(LONG) }).containsExactly(*bytesOf(0xBC, 0x0B))
        }

        @Test
        fun `refuses an array of void, which has no element to hold`() {
            assertThatIllegalArgumentException()
                .isThrownBy { bytecode { +newarray(VOID) } }
                .withMessage("an array cannot hold void")
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
