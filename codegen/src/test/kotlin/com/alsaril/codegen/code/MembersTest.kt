package com.alsaril.codegen.code

import com.alsaril.codegen.bytesOf
import com.alsaril.codegen.constantpool.*
import com.alsaril.codegen.instruction.*
import com.alsaril.codegen.verification.PrimitiveType.*
import com.alsaril.codegen.verification.ReferenceType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.alsaril.codegen.classfile.PrimitiveType as ElementType

/**
 * The helpers that turn a class, a name and a descriptor into constant pool refs and the
 * instructions that use them. A call helper emits its instruction straight away, so what it
 * did is read off the instruction: the ref it points at and the types it takes and leaves.
 */
class MembersTest {

    private val a = ReferenceType("A")

    private fun CodeBuilder.emitted() = build().instructions

    @Nested
    inner class Calls {

        @Test
        fun `emit invokevirtual against a method ref, taking the owner as the receiver`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            builder.invokevirtual(builder.clazz("A"), "f", "(I)V")

            // then the receiver is on the stack under the arguments, so it is an operand too
            assertThat(builder.emitted()).containsExactly(invokevirtual(6, listOf(a, INTEGER), VOID))
            assertThat(cp.build().entries).containsExactly(
                ConstantUtf8Info("A"),
                ConstantClassInfo(nameIndex = 1),
                ConstantUtf8Info("f"),
                ConstantUtf8Info("(I)V"),
                ConstantNameAndTypeInfo(nameIndex = 3, descriptorIndex = 4),
                ConstantMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
            )
        }

        @Test
        fun `emit invokeinterface against an interface method ref`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            builder.invokeinterface(builder.clazz("A"), "f", "(I)V")

            // then
            assertThat(builder.emitted()).containsExactly(invokeinterface(6, listOf(a, INTEGER), VOID))
            assertThat(cp.build().entries).last()
                .isEqualTo(ConstantInterfaceMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5))
        }

        @Test
        fun `emit invokestatic with no receiver among its operands`() {
            // given
            val builder = builder()

            // when
            builder.invokestatic(builder.clazz("A"), "f", "(I)V")

            // then
            assertThat(builder.emitted()).containsExactly(invokestatic(6, listOf(INTEGER), VOID))
        }

        @Test
        fun `emit invokespecial marked with the class a constructor builds`() {
            // given
            val builder = builder()
            val owner = builder.clazz("A")

            // when a constructor is called, and then a method that is not one
            builder.invokespecial(owner, "<init>", "()V")
            builder.invokespecial(owner, "helper", "()V")

            // then only <init> initialises what it is called on
            assertThat(builder.emitted()).containsExactly(
                invokespecial(6, listOf(a), VOID, constructorFor = a),
                invokespecial(9, listOf(a), VOID, constructorFor = null),
            )
        }

        @Test
        fun `take the arguments and the result from the descriptor`() {
            // given
            val builder = builder()

            // when
            builder.invokestatic(builder.clazz("A"), "f", "(IJLjava/lang/String;[I)F")

            // then
            assertThat(builder.emitted()).containsExactly(
                invokestatic(6, listOf(INTEGER, LONG, ReferenceType("java/lang/String"), ReferenceType("[I")), FLOAT),
            )
        }

        @Test
        fun `keep a method and an interface method of the same name apart`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)
            val clazz = builder.clazz("A")

            // when
            builder.invokevirtual(clazz, "f", "()V")
            builder.invokeinterface(clazz, "f", "()V")

            // then the two refs differ while sharing one name-and-type
            assertThat(builder.emitted()).containsExactly(
                invokevirtual(6, listOf(a), VOID),
                invokeinterface(7, listOf(a), VOID),
            )
            assertThat(cp.build().entries).endsWith(
                ConstantMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                ConstantInterfaceMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
            )
        }

        @Test
        fun `reuse one pool entry for a repeated call`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            builder.invokevirtual(builder.clazz("A"), "f", "()V")
            builder.invokevirtual(builder.clazz("A"), "f", "()V")

            // then
            assertThat(builder.emitted()).containsExactly(
                invokevirtual(6, listOf(a), VOID),
                invokevirtual(6, listOf(a), VOID),
            )
            assertThat(cp.build().entries).hasSize(6)
        }

        @Test
        fun `emit invokedynamic against a call site entry, with no receiver among its operands`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            builder.invokedynamic("make", "(IJ)LA;", BootstrapPointer(3))

            // then the arguments alone, and the result
            assertThat(builder.emitted()).containsExactly(invokedynamic(4, listOf(INTEGER, LONG), a))
            assertThat(cp.build().entries).containsExactly(
                ConstantUtf8Info("make"),
                ConstantUtf8Info("(IJ)LA;"),
                ConstantNameAndTypeInfo(nameIndex = 1, descriptorIndex = 2),
                ConstantInvokeDynamicInfo(bootstrapMethodIndex = 3, nameAndTypeIndex = 3),
            )
        }

        @Test
        fun `emit one invokedynamic per call, sharing the pool entry of an equal call site`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            builder.invokedynamic("make", "()V", BootstrapPointer(0))
            builder.invokedynamic("make", "()V", BootstrapPointer(0))

            // then the JVM links each instruction on its own, so the entry is all they share
            assertThat(builder.emitted()).containsExactly(invokedynamic(4, emptyList(), VOID), invokedynamic(4, emptyList(), VOID))
            assertThat(cp.build().entries).hasSize(4)
        }

        @Test
        fun `keep call sites of different bootstrap methods apart`() {
            // given
            val builder = builder()

            // when
            builder.invokedynamic("make", "()V", BootstrapPointer(0))
            builder.invokedynamic("make", "()V", BootstrapPointer(1))

            // then
            assertThat(builder.emitted().map { (it as invokedynamic).index }).doesNotHaveDuplicates()
        }

        @Test
        fun `hand back the label of the call they emit`() {
            // given one instruction ahead of every call, so each lands at index 1
            val calls = listOf<CodeBuilder.(ClassPointer) -> Label>(
                { invokevirtual(it, "f", "()V") },
                { invokespecial(it, "f", "()V") },
                { invokestatic(it, "f", "()V") },
                { invokeinterface(it, "f", "()V") },
                { invokedynamic("f", "()V", BootstrapPointer(0)) },
            )

            // then like every other emitter, so a call can be a branch target or open a guarded range
            calls.forEach { call ->
                val builder = builder()
                with(builder) { +nop }
                val label = builder.call(builder.clazz("A"))
                assertThat(builder.indexOf(label)).isOne()
            }
        }
    }

    @Nested
    inner class Fields {

        @Test
        fun `register a field ref in the constant pool`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            val field = builder.field(builder.clazz("A"), "x", "I")

            // then the field is read and written on an object of the class that owns it
            assertThat(field).isEqualTo(FieldDescriptor(6, a, INTEGER))
            assertThat(cp.build().entries).last()
                .isEqualTo(ConstantFieldRefInfo(classNameIndex = 2, nameAndTypeIndex = 5))
        }

        @Test
        fun `take the type a field holds from its descriptor`() {
            // given
            val builder = builder()
            val owner = builder.clazz("A")

            // then
            assertThat(builder.field(owner, "x", "Z").type).isEqualTo(INTEGER)
            assertThat(builder.field(owner, "x", "J").type).isEqualTo(LONG)
            assertThat(builder.field(owner, "x", "D").type).isEqualTo(DOUBLE)
            assertThat(builder.field(owner, "x", "Ljava/lang/String;").type).isEqualTo(ReferenceType("java/lang/String"))
            assertThat(builder.field(owner, "x", "[J").type).isEqualTo(ReferenceType("[J"))
        }
    }

    @Nested
    inner class Instructions {

        @Test
        fun `writes the invoke family with the ref index`() {
            assertThat(bytecode { +invokevirtual(0x0102, emptyList(), VOID) })
                .containsExactly(*bytesOf(0xB6, 0x01, 0x02))
            assertThat(bytecode { +invokespecial(1, emptyList(), VOID, null) })
                .containsExactly(*bytesOf(0xB7, 0x00, 0x01))
            assertThat(bytecode { +invokestatic(1, emptyList(), VOID) })
                .containsExactly(*bytesOf(0xB8, 0x00, 0x01))
        }

        @Test
        fun `writes invokedynamic with the call site index and two zero bytes`() {
            // the two bytes after the index are reserved, and JVMS has them zero
            assertThat(bytecode { +invokedynamic(0x0102, emptyList(), VOID) })
                .containsExactly(*bytesOf(0xBA, 0x01, 0x02, 0x00, 0x00))
        }

        @Test
        fun `refuses invokedynamic past a two byte index`() {
            assertThatIllegalArgumentException()
                .isThrownBy { invokedynamic(65536, emptyList(), VOID) }
                .withMessage("65536 does not fit a u2")
        }

        @Test
        fun `writes invokeinterface with its argument count and trailing zero`() {
            // the count is in slots, receiver included, so a long argument adds two
            assertThat(bytecode { +invokeinterface(1, listOf(a, INTEGER), VOID) })
                .containsExactly(*bytesOf(0xB9, 0x00, 0x01, 0x02, 0x00))
            assertThat(bytecode { +invokeinterface(1, listOf(a, LONG, DOUBLE), VOID) })
                .containsExactly(*bytesOf(0xB9, 0x00, 0x01, 0x05, 0x00))
        }

        @Test
        fun `writes getstatic with the field index`() {
            assertThat(bytecode { +getstatic(FieldDescriptor(3, a, INTEGER)) })
                .containsExactly(*bytesOf(0xB2, 0x00, 0x03))
        }

        @Test
        fun `writes getfield with the field index`() {
            assertThat(bytecode { +getfield(FieldDescriptor(3, a, INTEGER)) })
                .containsExactly(*bytesOf(0xB4, 0x00, 0x03))
            assertThat(bytecode { +getfield(FieldDescriptor(0x0102, a, LONG)) })
                .containsExactly(*bytesOf(0xB4, 0x01, 0x02))
        }

        @Test
        fun `writes putfield with the field index`() {
            assertThat(bytecode { +putfield(FieldDescriptor(3, a, INTEGER)) })
                .containsExactly(*bytesOf(0xB5, 0x00, 0x03))
            assertThat(bytecode { +putfield(FieldDescriptor(0x0102, a, LONG)) })
                .containsExactly(*bytesOf(0xB5, 0x01, 0x02))
        }

        @Test
        fun `writes new with the class index`() {
            assertThat(bytecode { +new(ClassPointer(4, "A")) }).containsExactly(*bytesOf(0xBB, 0x00, 0x04))
        }

        @Test
        fun `writes checkcast with the class index`() {
            assertThat(bytecode { +checkcast(ClassPointer(4, "A")) })
                .containsExactly(*bytesOf(0xC0, 0x00, 0x04))
        }

        @Test
        fun `writes instanceof with the class index`() {
            assertThat(bytecode { +instanceof(ClassPointer(4, "A")) })
                .containsExactly(*bytesOf(0xC1, 0x00, 0x04))
        }

        @Test
        fun `writes anewarray with the class index`() {
            assertThat(bytecode { +anewarray(ClassPointer(4, "A")) })
                .containsExactly(*bytesOf(0xBD, 0x00, 0x04))
        }

        @Test
        fun `writes newarray with the atype of its element`() {
            // the codes JVMS 6.5 lists for newarray, T_BOOLEAN through T_LONG
            assertThat(bytecode { +newarray(ElementType.BOOLEAN) }).containsExactly(*bytesOf(0xBC, 0x04))
            assertThat(bytecode { +newarray(ElementType.CHAR) }).containsExactly(*bytesOf(0xBC, 0x05))
            assertThat(bytecode { +newarray(ElementType.FLOAT) }).containsExactly(*bytesOf(0xBC, 0x06))
            assertThat(bytecode { +newarray(ElementType.DOUBLE) }).containsExactly(*bytesOf(0xBC, 0x07))
            assertThat(bytecode { +newarray(ElementType.BYTE) }).containsExactly(*bytesOf(0xBC, 0x08))
            assertThat(bytecode { +newarray(ElementType.SHORT) }).containsExactly(*bytesOf(0xBC, 0x09))
            assertThat(bytecode { +newarray(ElementType.INTEGER) }).containsExactly(*bytesOf(0xBC, 0x0A))
            assertThat(bytecode { +newarray(ElementType.LONG) }).containsExactly(*bytesOf(0xBC, 0x0B))
        }

        @Test
        fun `refuses an array of void, which has no element to hold`() {
            assertThatIllegalArgumentException()
                .isThrownBy { bytecode { +newarray(ElementType.VOID) } }
                .withMessage("an array cannot hold void")
        }

        @Test
        fun `expands constructDefault into new, dup and the no-argument constructor call`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            builder.constructDefault(builder.clazz("A"))

            // then class A is index 2 and the method ref lands at 6
            assertThat(builder.build().bytecode()).containsExactly(
                *bytesOf(
                    0xBB, 0x00, 0x02,  // new A
                    0x59,              // dup
                    0xB7, 0x00, 0x06,  // invokespecial <init>
                ),
            )
        }

        @Test
        fun `points constructDefault at the no-argument constructor of the class`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            builder.constructDefault(builder.clazz("A"))

            // then
            assertThat(cp.build().entries).containsExactly(
                ConstantUtf8Info("A"),
                ConstantClassInfo(nameIndex = 1),
                ConstantUtf8Info("<init>"),
                ConstantUtf8Info("()V"),
                ConstantNameAndTypeInfo(nameIndex = 3, descriptorIndex = 4),
                ConstantMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
            )
        }
    }
}
