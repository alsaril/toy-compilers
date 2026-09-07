package com.alsaril.codegen.classfile

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.bytesOf
import com.alsaril.codegen.classfile.attributes.AppendFrame
import com.alsaril.codegen.classfile.attributes.AttributeInfo
import com.alsaril.codegen.classfile.attributes.CodeAttribute
import com.alsaril.codegen.classfile.attributes.FullFrame
import com.alsaril.codegen.classfile.attributes.ObjectVariableInfo
import com.alsaril.codegen.classfile.attributes.SameFrame
import com.alsaril.codegen.classfile.attributes.SameFrameExtended
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.IntegerVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.TopVariableInfo
import com.alsaril.codegen.classfile.attributes.StackMapTableAttribute
import com.alsaril.codegen.classfile.attributes.sameFrame
import com.alsaril.codegen.constantpool.ConstantIntegerInfo
import com.alsaril.codegen.constantpool.StaticConstantPool
import com.alsaril.codegen.serialized
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Byte-level expectations come from the JVMS 4.1 - 4.7 structures, spelled out
 * literally rather than produced by a second implementation of the same encoding.
 */
class ClassFileSerializationTest {

    /** Minimal attribute used to observe the framing the base class applies. */
    private class RawAttribute(nameIndex: Int, private val content: ByteArray) : AttributeInfo(nameIndex) {
        override fun ClassWriter.writeContent() = bytes(content)
    }

    @Nested
    inner class Attributes {

        @Test
        fun `frames content with a name index and a four-byte length`() {
            assertThat(RawAttribute(1, bytesOf(0xAA, 0xBB)).serialized())
                .containsExactly(*bytesOf(0x00, 0x01, 0x00, 0x00, 0x00, 0x02, 0xAA, 0xBB))
        }

        @Test
        fun `writes a zero length for empty content`() {
            assertThat(RawAttribute(7, bytesOf()).serialized())
                .containsExactly(*bytesOf(0x00, 0x07, 0x00, 0x00, 0x00, 0x00))
        }

        @Test
        fun `writes code with limits, bytecode and an empty exception table`() {
            // given
            val code = CodeAttribute(
                nameIndex = 1,
                maxStack = 2,
                maxLocals = 3,
                code = bytesOf(0xB1),
                attributes = emptyList(),
            )

            // then
            assertThat(code.serialized()).containsExactly(
                *bytesOf(
                    0x00, 0x01,              // attribute name index
                    0x00, 0x00, 0x00, 0x0D,  // attribute length
                    0x00, 0x02,              // max_stack
                    0x00, 0x03,              // max_locals
                    0x00, 0x00, 0x00, 0x01,  // code_length
                    0xB1,                    // return
                    0x00, 0x00,              // exception_table_length
                    0x00, 0x00,              // attributes_count
                ),
            )
        }

        /**
         * code_length is a u4 on the wire, so the writer cannot catch this; the JVMS caps
         * a method at 65535 bytes and only the attribute itself knows that.
         */
        @Test
        fun `rejects a method past the 65535 byte limit`() {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy {
                    CodeAttribute(
                        nameIndex = 1,
                        maxStack = 0,
                        maxLocals = 0,
                        code = ByteArray(65_536),
                        attributes = emptyList(),
                    )
                }
                .withMessageContaining("over the 65535 limit")

            assertThatNoException().isThrownBy {
                CodeAttribute(1, 0, 0, ByteArray(65_535), emptyList())
            }
        }

        @Test
        fun `grows the code attribute length to cover nested attributes`() {
            // given
            val code = CodeAttribute(
                nameIndex = 1,
                maxStack = 0,
                maxLocals = 0,
                code = bytesOf(),
                attributes = listOf(RawAttribute(9, bytesOf(0x2A))),
            )

            // then
            assertThat(code.serialized()).containsExactly(
                *bytesOf(
                    0x00, 0x01,
                    0x00, 0x00, 0x00, 0x13,  // 12 fixed bytes plus the 7 byte nested attribute
                    0x00, 0x00,
                    0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00,
                    0x00, 0x01,              // attributes_count
                    0x00, 0x09, 0x00, 0x00, 0x00, 0x01, 0x2A,
                ),
            )
        }

        @Test
        fun `writes a stack map table as a count followed by its frames`() {
            // given
            val table = StackMapTableAttribute(nameIndex = 5, entries = listOf(SameFrame(0), SameFrame(1)))

            // then
            assertThat(table.serialized()).containsExactly(
                *bytesOf(
                    0x00, 0x05,
                    0x00, 0x00, 0x00, 0x04,  // count plus two one-byte frames
                    0x00, 0x02,              // number_of_entries
                    0x00, 0x01,
                ),
            )
        }
    }

    @Nested
    inner class Frames {

        @Test
        fun `writes a same frame as a single byte`() {
            assertThat(SameFrame(0).serialized()).containsExactly(*bytesOf(0x00))
            assertThat(SameFrame(63).serialized()).containsExactly(*bytesOf(0x3F))
        }

        @Test
        fun `rejects a same frame outside the single byte range`() {
            assertThatIllegalArgumentException().isThrownBy { SameFrame(64) }
            assertThatIllegalArgumentException().isThrownBy { SameFrame(-1) }
        }

        @Test
        fun `writes an extended same frame as a tag and an offset`() {
            assertThat(SameFrameExtended(300).serialized())
                .containsExactly(*bytesOf(0xFB, 0x01, 0x2C))
        }

        @Test
        fun `picks the compact same frame up to an offset of 63`() {
            assertThat(sameFrame(63)).isEqualTo(SameFrame(63))
            assertThat(sameFrame(63).serialized()).hasSize(1)
        }

        @Test
        fun `switches to the extended same frame past an offset of 63`() {
            assertThat(sameFrame(64)).isEqualTo(SameFrameExtended(64))
            assertThat(sameFrame(64).serialized()).containsExactly(*bytesOf(0xFB, 0x00, 0x40))
        }

        @Test
        fun `encodes the local count into the append frame tag`() {
            assertThat(AppendFrame(5, listOf(IntegerVariableInfo)).serialized())
                .containsExactly(*bytesOf(0xFC, 0x00, 0x05, 0x01))
            assertThat(AppendFrame(5, List(3) { IntegerVariableInfo }).serialized())
                .containsExactly(*bytesOf(0xFE, 0x00, 0x05, 0x01, 0x01, 0x01))
        }

        @Test
        fun `rejects an append frame with more than three locals`() {
            assertThatIllegalArgumentException().isThrownBy {
                AppendFrame(0, List(4) { IntegerVariableInfo })
            }
        }

        @Test
        fun `rejects an append frame with no locals`() {
            assertThatIllegalArgumentException().isThrownBy { AppendFrame(0, emptyList()) }
        }

        @Test
        fun `writes a full frame with both locals and stack`() {
            // given
            val frame = FullFrame(
                offsetDelta = 1,
                locals = listOf(IntegerVariableInfo),
                stack = listOf(ObjectVariableInfo(3)),
            )

            // then
            assertThat(frame.serialized()).containsExactly(
                *bytesOf(
                    0xFF,        // frame_type
                    0x00, 0x01,  // offset_delta
                    0x00, 0x01,  // number_of_locals
                    0x01,        // integer
                    0x00, 0x01,  // number_of_stack_items
                    0x07, 0x00, 0x03,
                ),
            )
        }
    }

    @Nested
    inner class VerificationTypes {

        @Test
        fun `writes the simple types as their tag`() {
            assertThat(TopVariableInfo.serialized()).containsExactly(*bytesOf(0x00))
            assertThat(IntegerVariableInfo.serialized()).containsExactly(*bytesOf(0x01))
        }

        @Test
        fun `writes an object type as a tag and a constant pool index`() {
            assertThat(ObjectVariableInfo(258).serialized())
                .containsExactly(*bytesOf(0x07, 0x01, 0x02))
        }
    }

    @Nested
    inner class Methods {

        @Test
        fun `writes flags, name, descriptor and an empty attribute list`() {
            // given
            val method = MethodInfo(
                accessFlags = 0x0009,
                nameIndex = 1,
                descriptorIndex = 2,
                attributes = emptyList(),
            )

            // then
            assertThat(method.serialized())
                .containsExactly(*bytesOf(0x00, 0x09, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00))
        }

        @Test
        fun `writes its attributes after the count`() {
            // given
            val method = MethodInfo(
                accessFlags = 0x0001,
                nameIndex = 1,
                descriptorIndex = 2,
                attributes = listOf(RawAttribute(3, bytesOf(0x2A))),
            )

            // then
            assertThat(method.serialized()).containsExactly(
                *bytesOf(
                    0x00, 0x01, 0x00, 0x01, 0x00, 0x02,
                    0x00, 0x01,  // attributes_count
                    0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x2A,
                ),
            )
        }

        @Test
        fun `keeps the jvms values for the access flags`() {
            assertThat(MethodAccessFlag.entries.map { it.value })
                .containsExactly(0x0001, 0x0002, 0x0008, 0x0010)
        }
    }

    @Nested
    inner class Classes {

        private val emptyPool = StaticConstantPool(1, emptyList())

        @Test
        fun `writes the header, pool, hierarchy and empty member lists`() {
            // given
            val file = ClassFile(
                thisClassIndex = 1,
                parentIndex = 2,
                ifaceIndexes = emptyList(),
                methods = emptyList(),
                constantPool = emptyPool,
            )

            // then
            assertThat(file.serialized()).containsExactly(
                *bytesOf(
                    0xCA, 0xFE, 0xBA, 0xBE,  // magic
                    0x00, 0x00,              // minor_version
                    0x00, 0x34,              // major_version: java 8
                    0x00, 0x01,              // constant_pool_count
                    0x00, 0x11,              // access_flags: public final
                    0x00, 0x01,              // this_class
                    0x00, 0x02,              // super_class
                    0x00, 0x00,              // interfaces_count
                    0x00, 0x00,              // fields_count
                    0x00, 0x00,              // methods_count
                    0x00, 0x00,              // attributes_count
                ),
            )
        }

        @Test
        fun `writes each interface index`() {
            // given
            val file = ClassFile(1, 2, listOf(3, 4), emptyList(), emptyPool)

            // then
            assertThat(file.serialized()).endsWith(
                *bytesOf(
                    0x00, 0x02, 0x00, 0x03, 0x00, 0x04,  // interfaces
                    0x00, 0x00,                          // fields_count
                    0x00, 0x00,                          // methods_count
                    0x00, 0x00,                          // attributes_count
                ),
            )
        }

        @Test
        fun `writes each method after the count`() {
            // given
            val method = MethodInfo(0x0001, 1, 2, emptyList())
            val file = ClassFile(1, 2, emptyList(), listOf(method, method), emptyPool)

            // then
            assertThat(file.serialized()).endsWith(
                *bytesOf(
                    0x00, 0x02,  // methods_count
                    0x00, 0x01, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00,
                    0x00, 0x01, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00,
                    0x00, 0x00,  // attributes_count
                ),
            )
        }

        @Test
        fun `embeds the constant pool between the version and the access flags`() {
            // given
            val pool = StaticConstantPool(2, listOf(ConstantIntegerInfo(1)))
            val file = ClassFile(1, 2, emptyList(), emptyList(), pool)

            // then
            assertThat(file.serialized()).startsWith(
                *bytesOf(
                    0xCA, 0xFE, 0xBA, 0xBE,
                    0x00, 0x00, 0x00, 0x34,
                    0x00, 0x02,                    // constant_pool_count
                    0x03, 0x00, 0x00, 0x00, 0x01,  // the single integer entry
                    0x00, 0x11,                    // access_flags follow the pool
                ),
            )
        }
    }
}
