package com.alsaril.codegen.code

import com.alsaril.codegen.bytesOf
import com.alsaril.codegen.classfile.attributes.AppendFrame
import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.classfile.attributes.FullFrame
import com.alsaril.codegen.classfile.attributes.ObjectVariableInfo
import com.alsaril.codegen.classfile.attributes.SameFrame
import com.alsaril.codegen.classfile.attributes.SameFrameExtended
import com.alsaril.codegen.classfile.attributes.SameLocals1StackItemFrameExtended
import com.alsaril.codegen.classfile.attributes.SameLocals1StackItemFrameShort
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.IntegerVariableInfo
import com.alsaril.codegen.classfile.attributes.StackMapFrame
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.alsaril.codegen.instruction.*


class FragmentTest {

    private fun fragment(size: Int, vararg frames: Pair<Int, StackMapFrame>) = builder().apply {
        val byIndex = frames.toMap()
        repeat(size) { i ->
            val label = +nop
            byIndex[i]?.let { frame(patchOffset(it, indexOf(label))) }
        }
    }.build()

    @Nested
    inner class Bytecode {

        @Test
        fun `lays the instructions out in order`() {
            // given
            val fragment = builder().apply { +aconst_null; +iconst(-1); +iconst(0); +iconst(1) }.build()

            // then
            assertThat(fragment.bytecode()).containsExactly(*bytesOf(0x01, 0x02, 0x03, 0x04))
        }

        @Test
        fun `is empty when there is no content`() {
            assertThat(Fragment(emptyList(), emptyMap(), emptyList(), emptyList(), size = 0).bytecode())
                .isEmpty()
        }
    }

    @Nested
    inner class Join {

        @Test
        fun `hands back a lone fragment untouched`() {
            // given
            val only = fragment(3, 1 to SameFrame(0))

            // then
            assertThat(listOf(only).join()).isSameAs(only)
        }

        @Test
        fun `produces an empty fragment for no input`() {
            // given
            val joined = emptyList<Fragment>().join()

            // then
            assertThat(joined.size).isZero()
            assertThat(joined.frames).isEmpty()
            assertThat(joined.exceptionHandlers).isEmpty()
            assertThat(joined.instructions).isEmpty()
        }

        @Test
        fun `adds up the sizes and keeps the instructions in order`() {
            // given
            val joined = listOf(
                builder().apply { +aconst_null; +iconst(-1) }.build(),
                builder().apply { +iconst(0) }.build(),
            ).join()

            // then
            assertThat(joined.size).isEqualTo(3)
            assertThat(joined.bytecode()).containsExactly(*bytesOf(0x01, 0x02, 0x03))
        }

        @Test
        fun `shifts the links of a fragment by instructions rather than bytes`() {
            // given a single instruction three bytes wide ahead of a fragment that
            // reaches its own target
            val prefix = builder().apply { +goto }.build()
            val body = builder().apply {
                val jump = +goto
                link(jump, +nop)
            }.build()

            // when
            val joined = listOf(prefix, body).join()

            // then the link moved by the one instruction ahead of it, not by its three bytes
            assertThat(joined.jumps).isEqualTo(mapOf(1 to 2))
            assertThat(joined.bytecode())
                .containsExactly(*bytesOf(0xA7, 0x00, 0x00, 0xA7, 0x00, 0x03, 0x00))
        }

        @Test
        fun `rewrites the first frame of a later fragment against the joined offsets`() {
            // given a frame at offset 1 of the first fragment and at offset 0 of the second
            val joined = listOf(
                fragment(3, 1 to SameFrame(0)),
                fragment(2, 0 to SameFrame(0)),
            ).join()

            // then both deltas are measured from one past the previous frame
            assertThat(framesOf(joined)).containsExactly(SameFrame(1), SameFrame(1))
        }

        @Test
        fun `leaves the frames after the first of a fragment alone`() {
            // given
            val joined = listOf(
                fragment(5, 0 to SameFrame(0), 3 to SameFrame(0)),
                fragment(1),
            ).join()

            // then only the first frame of a fragment needs measuring against the join
            assertThat(framesOf(joined)).containsExactly(SameFrame(0), SameFrame(2))
        }

        @Test
        fun `re-picks the compact form when patching an extended same frame`() {
            // given
            val joined = listOf(
                fragment(2),
                fragment(1, 0 to SameFrameExtended(0)),
            ).join()

            // then an offset that now fits a byte goes back to the compact form
            assertThat(framesOf(joined)).containsExactly(SameFrame(2))
        }

        @Test
        fun `patches an append frame`() {
            // given
            val joined = listOf(
                fragment(2),
                fragment(1, 0 to AppendFrame(0, listOf(IntegerVariableInfo))),
            ).join()

            // then
            assertThat(framesOf(joined))
                .containsExactly(AppendFrame(2, listOf(IntegerVariableInfo)))
        }

        @Test
        fun `patches a stack frame`() {
            // given
            val joined = listOf(
                fragment(2),
                fragment(1, 0 to SameLocals1StackItemFrameShort(0, IntegerVariableInfo)),
            ).join()

            // then
            assertThat(framesOf(joined))
                .containsExactly(SameLocals1StackItemFrameShort(2, IntegerVariableInfo))
        }

        @Test
        fun `re-picks the compact form when patching an extended stack frame`() {
            // given
            val joined = listOf(
                fragment(2),
                fragment(1, 0 to SameLocals1StackItemFrameExtended(0, IntegerVariableInfo)),
            ).join()

            // then, as with a same frame, an offset that fits a byte goes back to the tag
            assertThat(framesOf(joined))
                .containsExactly(SameLocals1StackItemFrameShort(2, IntegerVariableInfo))
        }

        @Test
        fun `patches a full frame`() {
            // given
            val frame = FullFrame(0, listOf(IntegerVariableInfo), listOf(ObjectVariableInfo(3)))
            val joined = listOf(fragment(2), fragment(1, 0 to frame)).join()

            // then
            assertThat(framesOf(joined)).containsExactly(frame.copy(offsetDelta = 2))
        }

        @Test
        fun `keeps a fragment without frames from shifting the ones after it`() {
            // given
            val joined = listOf(
                fragment(2, 0 to SameFrame(0)),
                fragment(3),
                fragment(1, 0 to SameFrame(0)),
            ).join()

            // then the frameless fragment still advances the offset the last frame sees
            assertThat(framesOf(joined)).containsExactly(SameFrame(0), SameFrame(4))
        }
    }

    @Nested
    inner class JoinedHandlers {

        // `size` nops guarded by rows already written against this fragment's own indices
        private fun guarded(size: Int, vararg handlers: ExceptionHandler) =
            Fragment(List(size) { nop }, emptyMap(), emptyList(), handlers.toList(), size)

        @Test
        fun `hands back a lone fragment's handlers untouched`() {
            // given
            val handler = ExceptionHandler(1, 2, 2, catchType = 3)
            val only = guarded(3, handler)

            // then
            assertThat(listOf(only).join().exceptionHandlers).containsExactly(handler)
        }

        @Test
        fun `leaves the handlers of the first fragment where they are`() {
            // given
            val joined = listOf(
                guarded(4, ExceptionHandler(0, 2, 3, catchType = 0)),
                guarded(1),
            ).join()

            // then
            assertThat(joined.exceptionHandlers)
                .containsExactly(ExceptionHandler(0, 2, 3, catchType = 0))
        }

        @Test
        fun `shifts every location by how far its fragment moved`() {
            // given a handler covering the whole of a fragment that lands at index 5
            val joined = listOf(
                guarded(5),
                guarded(4, ExceptionHandler(0, 2, 3, catchType = 7)),
            ).join()

            // then the range and the handler move together, and the caught type does not
            assertThat(joined.exceptionHandlers)
                .containsExactly(ExceptionHandler(5, 7, 8, catchType = 7))
        }

        @Test
        fun `collects the handlers of every fragment in order`() {
            // given
            val joined = listOf(
                guarded(2, ExceptionHandler(0, 1, 1, catchType = 0)),
                guarded(2, ExceptionHandler(0, 1, 1, catchType = 0)),
                guarded(2, ExceptionHandler(0, 1, 1, catchType = 0)),
            ).join()

            // then the order the jvm searches them in survives the join
            assertThat(joined.exceptionHandlers).containsExactly(
                ExceptionHandler(0, 1, 1, catchType = 0),
                ExceptionHandler(2, 3, 3, catchType = 0),
                ExceptionHandler(4, 5, 5, catchType = 0),
            )
        }

        @Test
        fun `keeps several handlers of one fragment together`() {
            // given
            val joined = listOf(
                guarded(1),
                guarded(
                    4,
                    ExceptionHandler(0, 1, 2, catchType = 0),
                    ExceptionHandler(1, 2, 3, catchType = 0),
                ),
            ).join()

            // then
            assertThat(joined.exceptionHandlers).containsExactly(
                ExceptionHandler(1, 2, 3, catchType = 0),
                ExceptionHandler(2, 3, 4, catchType = 0),
            )
        }

        @Test
        fun `moves handlers and frames by the same amount`() {
            // given a fragment carrying both
            val body = Fragment(
                List(3) { nop },
                emptyMap(),
                listOf(SameLocals1StackItemFrameShort(1, IntegerVariableInfo)),
                listOf(ExceptionHandler(0, 1, 1, catchType = 0)),
                size = 3,
            )
            val joined = listOf(fragment(2), body).join()

            // then the frame delta counts from the previous frame and the handler from zero
            assertThat(framesOf(joined))
                .containsExactly(SameLocals1StackItemFrameShort(3, IntegerVariableInfo))
            assertThat(joined.exceptionHandlers)
                .containsExactly(ExceptionHandler(2, 3, 3, catchType = 0))
        }
    }
}
