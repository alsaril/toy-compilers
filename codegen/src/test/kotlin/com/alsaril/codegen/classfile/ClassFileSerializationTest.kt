package com.alsaril.codegen.classfile

import com.alsaril.codegen.constantpool.ClassPointer
import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.bytesOf
import com.alsaril.codegen.classfile.attributes.AppendFrame
import com.alsaril.codegen.classfile.attributes.AttributeInfo
import com.alsaril.codegen.classfile.attributes.BootstrapMethod
import com.alsaril.codegen.classfile.attributes.BootstrapMethodsAttribute
import com.alsaril.codegen.classfile.attributes.CodeAttribute
import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.classfile.attributes.FullFrame
import com.alsaril.codegen.classfile.attributes.ObjectVariableInfo
import com.alsaril.codegen.classfile.attributes.SameFrame
import com.alsaril.codegen.classfile.attributes.SameFrameExtended
import com.alsaril.codegen.classfile.attributes.SameLocals1StackItemFrameExtended
import com.alsaril.codegen.classfile.attributes.SameLocals1StackItemFrameShort
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.DoubleVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.FloatVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.IntegerVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.LongVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.NullVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.TopVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.UninitializedThis
import com.alsaril.codegen.classfile.attributes.StackMapTableAttribute
import com.alsaril.codegen.classfile.attributes.UninitializedVariableInfo
import com.alsaril.codegen.classfile.attributes.patchOffset
import com.alsaril.codegen.classfile.attributes.patchUninitialized
import com.alsaril.codegen.classfile.attributes.sameFrame
import com.alsaril.codegen.classfile.attributes.sameLocals1StackItem
import com.alsaril.codegen.constantpool.ConstantIntegerInfo
import com.alsaril.codegen.constantpool.StaticConstantPool
import com.alsaril.codegen.serialized
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatNoException
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
        fun `writes bootstrap methods as a count followed by each method and its arguments`() {
            // given one bootstrap method taking two arguments and one taking none
            val attribute = BootstrapMethodsAttribute(
                nameIndex = 1,
                entries = listOf(BootstrapMethod(2, listOf(3, 258)), BootstrapMethod(4, emptyList())),
            )

            // then
            assertThat(attribute.serialized()).containsExactly(
                *bytesOf(
                    0x00, 0x01,              // attribute_name_index
                    0x00, 0x00, 0x00, 0x0E,  // attribute_length
                    0x00, 0x02,              // num_bootstrap_methods
                    0x00, 0x02,              // bootstrap_method_ref
                    0x00, 0x02,              // num_bootstrap_arguments
                    0x00, 0x03, 0x01, 0x02,  // bootstrap_arguments
                    0x00, 0x04,              // bootstrap_method_ref
                    0x00, 0x00,              // num_bootstrap_arguments
                ),
            )
        }

        @Test
        fun `writes an empty bootstrap method table as a zero count`() {
            assertThat(BootstrapMethodsAttribute(1, emptyList()).serialized())
                .containsExactly(*bytesOf(0x00, 0x01, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00))
        }

        @Test
        fun `writes code with limits, bytecode and an empty exception table`() {
            // given
            val code = CodeAttribute(
                nameIndex = 1,
                maxStack = 2,
                maxLocals = 3,
                code = bytesOf(0xB1),
                exceptionHandlers = emptyList(),
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

        @Test
        fun `writes each exception handler after the table length`() {
            // given
            val code = CodeAttribute(
                nameIndex = 1,
                maxStack = 0,
                maxLocals = 0,
                code = bytesOf(0xB1),
                exceptionHandlers = listOf(
                    ExceptionHandler(startPc = 0, endPc = 1, handlerPc = 1, catchType = null),
                    ExceptionHandler(startPc = 2, endPc = 3, handlerPc = 4, catchType = ClassPointer(5, "E")),
                ),
                attributes = emptyList(),
            )

            // then
            assertThat(code.serialized()).containsExactly(
                *bytesOf(
                    0x00, 0x01,
                    0x00, 0x00, 0x00, 0x1D,  // 13 as before, plus 8 bytes per handler
                    0x00, 0x00,              // max_stack
                    0x00, 0x00,              // max_locals
                    0x00, 0x00, 0x00, 0x01,  // code_length
                    0xB1,
                    0x00, 0x02,              // exception_table_length
                    0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                    0x00, 0x02, 0x00, 0x03, 0x00, 0x04, 0x00, 0x05,
                    0x00, 0x00,              // attributes_count
                ),
            )
        }

        /**
         * code_length is a u4 on the wire, so the writer cannot catch either bound; the
         * JVMS caps a method at 1..65535 bytes and only the attribute itself knows that.
         */
        @Test
        fun `refuses a method past the 65535 byte limit`() {
            assertThatIllegalArgumentException()
                .isThrownBy {
                    CodeAttribute(
                        nameIndex = 1,
                        maxStack = 0,
                        maxLocals = 0,
                        code = ByteArray(65_536),
                        exceptionHandlers = emptyList(),
                        attributes = emptyList(),
                    )
                }
                .withMessageContaining("over the 65535 limit")

            assertThatNoException().isThrownBy {
                CodeAttribute(1, 0, 0, ByteArray(65_535), emptyList(), emptyList())
            }
        }

        @Test
        fun `refuses a method with no code at all`() {
            // the jvm rejects this at load time with a ClassFormatError, far from the
            // emitter that produced it, so the attribute refuses it at construction
            assertThatIllegalArgumentException()
                .isThrownBy {
                    CodeAttribute(
                        nameIndex = 1,
                        maxStack = 0,
                        maxLocals = 0,
                        code = bytesOf(),
                        exceptionHandlers = emptyList(),
                        attributes = emptyList(),
                    )
                }
                .withMessageContaining("code_length must be at least 1")

            assertThatNoException().isThrownBy {
                CodeAttribute(1, 0, 0, bytesOf(0xB1), emptyList(), emptyList())
            }
        }

        @Test
        fun `grows the code attribute length to cover nested attributes`() {
            // given
            val code = CodeAttribute(
                nameIndex = 1,
                maxStack = 0,
                maxLocals = 0,
                code = bytesOf(0xB1),
                exceptionHandlers = emptyList(),
                attributes = listOf(RawAttribute(9, bytesOf(0x2A))),
            )

            // then
            assertThat(code.serialized()).containsExactly(
                *bytesOf(
                    0x00, 0x01,
                    0x00, 0x00, 0x00, 0x14,  // 12 fixed bytes, one of code, and the 7 byte attribute
                    0x00, 0x00,
                    0x00, 0x00,
                    0x00, 0x00, 0x00, 0x01,
                    0xB1,
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
        fun `refuses a same frame outside the single byte range`() {
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
        fun `writes a compact stack frame as a tag past the same frames`() {
            // the 64..127 tags are an offset of 0..63 with one item on the stack
            assertThat(SameLocals1StackItemFrameShort(0, IntegerVariableInfo).serialized())
                .containsExactly(*bytesOf(0x40, 0x01))
            assertThat(SameLocals1StackItemFrameShort(63, ObjectVariableInfo(3)).serialized())
                .containsExactly(*bytesOf(0x7F, 0x07, 0x00, 0x03))
        }

        @Test
        fun `refuses a compact stack frame outside the single byte range`() {
            assertThatIllegalArgumentException()
                .isThrownBy { SameLocals1StackItemFrameShort(64, IntegerVariableInfo) }
            assertThatIllegalArgumentException()
                .isThrownBy { SameLocals1StackItemFrameShort(-1, IntegerVariableInfo) }
        }

        @Test
        fun `writes an extended stack frame as a tag, an offset and the item`() {
            assertThat(SameLocals1StackItemFrameExtended(300, ObjectVariableInfo(3)).serialized())
                .containsExactly(*bytesOf(0xF7, 0x01, 0x2C, 0x07, 0x00, 0x03))
        }

        @Test
        fun `picks the compact stack frame up to an offset of 63`() {
            assertThat(sameLocals1StackItem(63, IntegerVariableInfo))
                .isEqualTo(SameLocals1StackItemFrameShort(63, IntegerVariableInfo))
        }

        @Test
        fun `switches to the extended stack frame past an offset of 63`() {
            assertThat(sameLocals1StackItem(64, IntegerVariableInfo))
                .isEqualTo(SameLocals1StackItemFrameExtended(64, IntegerVariableInfo))
        }

        @Test
        fun `encodes the local count into the append frame tag`() {
            assertThat(AppendFrame(5, listOf(IntegerVariableInfo)).serialized())
                .containsExactly(*bytesOf(0xFC, 0x00, 0x05, 0x01))
            assertThat(AppendFrame(5, List(3) { IntegerVariableInfo }).serialized())
                .containsExactly(*bytesOf(0xFE, 0x00, 0x05, 0x01, 0x01, 0x01))
        }

        @Test
        fun `refuses an append frame with more than three locals`() {
            assertThatIllegalArgumentException().isThrownBy {
                AppendFrame(0, List(4) { IntegerVariableInfo })
            }
        }

        @Test
        fun `refuses an append frame with no locals`() {
            assertThatIllegalArgumentException().isThrownBy { AppendFrame(0, emptyList()) }
        }

        @Test
        fun `says what a frame it refuses could have held`() {
            assertThatIllegalArgumentException()
                .isThrownBy { SameFrame(64) }
                .withMessage("a same_frame holds an offset delta of 0..63, not 64")
            assertThatIllegalArgumentException()
                .isThrownBy { SameLocals1StackItemFrameShort(64, IntegerVariableInfo) }
                .withMessage("a same_locals_1_stack_item_frame holds an offset delta of 0..63, not 64")
            assertThatIllegalArgumentException()
                .isThrownBy { AppendFrame(0, List(4) { IntegerVariableInfo }) }
                .withMessage("an append_frame adds 1..3 locals, not 4")
        }

        @Test
        fun `moves a frame to a new offset, keeping what it describes`() {
            val locals = listOf(IntegerVariableInfo)

            assertThat(AppendFrame(0, locals).patchOffset(9)).isEqualTo(AppendFrame(9, locals))
            assertThat(FullFrame(0, locals, locals).patchOffset(300)).isEqualTo(FullFrame(300, locals, locals))
            assertThat(SameLocals1StackItemFrameShort(0, IntegerVariableInfo).patchOffset(9))
                .isEqualTo(SameLocals1StackItemFrameShort(9, IntegerVariableInfo))
        }

        @Test
        fun `re-picks the compact or the extended form for the offset it moves to`() {
            assertThat(SameFrame(3).patchOffset(64)).isEqualTo(SameFrameExtended(64))
            assertThat(SameFrameExtended(300).patchOffset(5)).isEqualTo(SameFrame(5))
            assertThat(SameLocals1StackItemFrameShort(3, IntegerVariableInfo).patchOffset(64))
                .isEqualTo(SameLocals1StackItemFrameExtended(64, IntegerVariableInfo))
            assertThat(SameLocals1StackItemFrameExtended(300, IntegerVariableInfo).patchOffset(5))
                .isEqualTo(SameLocals1StackItemFrameShort(5, IntegerVariableInfo))
        }

        @Test
        fun `points every uninitialised type at the byte offset of its new`() {
            // given uninitialised types naming the instructions at indices 1 and 2
            val frame = FullFrame(
                offsetDelta = 0,
                locals = listOf(UninitializedVariableInfo(1), IntegerVariableInfo),
                stack = listOf(UninitializedVariableInfo(2), UninitializedVariableInfo(1)),
            )

            // when those instructions are laid out at bytes 4 and 7
            val patched = frame.patchUninitialized(mapOf(1 to 4, 2 to 7))

            // then every copy moves, in both halves, and nothing else changes
            assertThat(patched).isEqualTo(
                FullFrame(
                    offsetDelta = 0,
                    locals = listOf(UninitializedVariableInfo(4), IntegerVariableInfo),
                    stack = listOf(UninitializedVariableInfo(7), UninitializedVariableInfo(4)),
                ),
            )
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
            assertThat(FloatVariableInfo.serialized()).containsExactly(*bytesOf(0x02))
        }

        @Test
        fun `writes the two slot, null and uninitialised this types as their tag`() {
            assertThat(DoubleVariableInfo.serialized()).containsExactly(*bytesOf(0x03))
            assertThat(LongVariableInfo.serialized()).containsExactly(*bytesOf(0x04))
            assertThat(NullVariableInfo.serialized()).containsExactly(*bytesOf(0x05))
            assertThat(UninitializedThis.serialized()).containsExactly(*bytesOf(0x06))
        }

        @Test
        fun `writes an object type as a tag and a constant pool index`() {
            assertThat(ObjectVariableInfo(258).serialized())
                .containsExactly(*bytesOf(0x07, 0x01, 0x02))
        }

        @Test
        fun `writes an uninitialised type as a tag and the offset of its new`() {
            assertThat(UninitializedVariableInfo(258).serialized())
                .containsExactly(*bytesOf(0x08, 0x01, 0x02))
        }
    }

    @Nested
    inner class ExceptionHandlers {

        @Test
        fun `writes the range, the handler and the caught type as four indexes`() {
            assertThat(ExceptionHandler(1, 258, 3, ClassPointer(4, "E")).serialized())
                .containsExactly(*bytesOf(0x00, 0x01, 0x01, 0x02, 0x00, 0x03, 0x00, 0x04))
        }

        @Test
        fun `writes a catch all as a zero type`() {
            assertThat(ExceptionHandler(0, 1, 1, catchType = null).serialized())
                .endsWith(*bytesOf(0x00, 0x00))
        }

        @Test
        fun `refuses a location past a u2`() {
            // the range itself is well formed, so the width is what is left to refuse
            assertThatIllegalArgumentException()
                .isThrownBy { ExceptionHandler(0x10000, 0x10001, 0, null).serialized() }
                .withMessageContaining("does not fit a u2")
        }

        @Test
        fun `refuses a range covering no instruction`() {
            // a location the jvm would refuse at load time, which no u2 check can see
            assertThatIllegalArgumentException()
                .isThrownBy { ExceptionHandler(4, 4, 8, null) }
                .withMessageContaining("[4, 4) covers no instruction")
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
            assertThat(AccessFlag.entries.map { it.value })
                .containsExactly(0x0001, 0x0002, 0x0008, 0x0010)
        }
    }

    @Nested
    inner class Fields {

        @Test
        fun `writes flags, name, descriptor and an empty attribute list`() {
            // given
            val field = FieldInfo(
                accessFlags = 0x0012,
                nameIndex = 1,
                descriptorIndex = 2,
                attributes = emptyList(),
            )

            // then
            assertThat(field.serialized())
                .containsExactly(*bytesOf(0x00, 0x12, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00))
        }

        @Test
        fun `writes its attributes after the count`() {
            // given
            val field = FieldInfo(
                accessFlags = 0x0002,
                nameIndex = 1,
                descriptorIndex = 2,
                attributes = listOf(RawAttribute(3, bytesOf(0x2A))),
            )

            // then
            assertThat(field.serialized()).containsExactly(
                *bytesOf(
                    0x00, 0x02, 0x00, 0x01, 0x00, 0x02,
                    0x00, 0x01,  // attributes_count
                    0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x2A,
                ),
            )
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
                fields = emptyList(),
                methods = emptyList(),
                attributes = emptyList(),
                constantPool = emptyPool,
            )

            // then
            assertThat(file.serialized()).containsExactly(
                *bytesOf(
                    0xCA, 0xFE, 0xBA, 0xBE,  // magic
                    0x00, 0x00,              // minor_version
                    0x00, 0x45,              // major_version: java 25
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
            val file = ClassFile(1, 2, listOf(3, 4), emptyList(), emptyList(), emptyList(), emptyPool)

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
            val file = ClassFile(1, 2, emptyList(), emptyList(), listOf(method, method), emptyList(), emptyPool)

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
        fun `writes each field after the count and before the methods`() {
            // given
            val field = FieldInfo(0x0002, 3, 4, emptyList())
            val method = MethodInfo(0x0001, 1, 2, emptyList())
            val file = ClassFile(1, 2, emptyList(), listOf(field, field), listOf(method), emptyList(), emptyPool)

            // then
            assertThat(file.serialized()).endsWith(
                *bytesOf(
                    0x00, 0x00,  // interfaces_count
                    0x00, 0x02,  // fields_count
                    0x00, 0x02, 0x00, 0x03, 0x00, 0x04, 0x00, 0x00,
                    0x00, 0x02, 0x00, 0x03, 0x00, 0x04, 0x00, 0x00,
                    0x00, 0x01,  // methods_count
                    0x00, 0x01, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00,
                    0x00, 0x00,  // attributes_count
                ),
            )
        }

        @Test
        fun `writes each attribute after the count, after the methods`() {
            // given
            val method = MethodInfo(0x0001, 1, 2, emptyList())
            val attributes = listOf(RawAttribute(5, bytesOf(0xAA)), RawAttribute(6, bytesOf()))
            val file = ClassFile(1, 2, emptyList(), emptyList(), listOf(method), attributes, emptyPool)

            // then
            assertThat(file.serialized()).endsWith(
                *bytesOf(
                    0x00, 0x01,  // methods_count
                    0x00, 0x01, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00,
                    0x00, 0x02,  // attributes_count
                    0x00, 0x05, 0x00, 0x00, 0x00, 0x01, 0xAA,
                    0x00, 0x06, 0x00, 0x00, 0x00, 0x00,
                ),
            )
        }

        @Test
        fun `embeds the constant pool between the version and the access flags`() {
            // given
            val pool = StaticConstantPool(2, listOf(ConstantIntegerInfo(1)))
            val file = ClassFile(1, 2, emptyList(), emptyList(), emptyList(), emptyList(), pool)

            // then
            assertThat(file.serialized()).startsWith(
                *bytesOf(
                    0xCA, 0xFE, 0xBA, 0xBE,
                    0x00, 0x00, 0x00, 0x45,
                    0x00, 0x02,                    // constant_pool_count
                    0x03, 0x00, 0x00, 0x00, 0x01,  // the single integer entry
                    0x00, 0x11,                    // access_flags follow the pool
                ),
            )
        }
    }
}
