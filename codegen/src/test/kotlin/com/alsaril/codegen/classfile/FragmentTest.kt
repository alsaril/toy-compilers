package com.alsaril.codegen.classfile

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

/**
 * A frame records how far it sits from the previous one, so joining fragments has to
 * rewrite the first frame of each against the offsets it lands on in the joined code.
 * An exception handler names absolute offsets instead, so all three of its locations
 * move by however far its fragment was pushed along.
 */
class FragmentTest {

    private fun fragment(size: Int, vararg frames: StackMapFrame) =
        Fragment(listOf(ByteArray(size)), frames.toList(), emptyList(), size)

    private fun guarded(size: Int, vararg handlers: ExceptionHandler) =
        Fragment(listOf(ByteArray(size)), emptyList(), handlers.toList(), size)

    @Nested
    inner class Bytecode {

        @Test
        fun `hands back a single block without copying it`() {
            // given
            val block = bytesOf(0x01, 0x02)
            val fragment = Fragment(listOf(block), emptyList(), emptyList(), 2)

            // then
            assertThat(fragment.bytecode()).isSameAs(block)
        }

        @Test
        fun `concatenates several blocks`() {
            // given
            val fragment = Fragment(
                listOf(bytesOf(0x01), bytesOf(0x02, 0x03), bytesOf(0x04)),
                emptyList(),
                emptyList(),
                4,
            )

            // then
            assertThat(fragment.bytecode()).containsExactly(*bytesOf(0x01, 0x02, 0x03, 0x04))
        }

        @Test
        fun `is empty when there is no content`() {
            assertThat(Fragment(emptyList(), emptyList(), emptyList(), 0).bytecode()).isEmpty()
        }
    }

    @Nested
    inner class Join {

        @Test
        fun `hands back a lone fragment untouched`() {
            // given
            val only = fragment(3, SameFrame(1))

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
            assertThat(joined.content).isEmpty()
        }

        @Test
        fun `adds up the sizes and keeps the blocks in order`() {
            // given
            val joined = listOf(
                Fragment(listOf(bytesOf(0x01, 0x02)), emptyList(), emptyList(), 2),
                Fragment(listOf(bytesOf(0x03)), emptyList(), emptyList(), 1),
            ).join()

            // then
            assertThat(joined.size).isEqualTo(3)
            assertThat(joined.bytecode()).containsExactly(*bytesOf(0x01, 0x02, 0x03))
        }

        @Test
        fun `rewrites the first frame of a later fragment against the joined offsets`() {
            // given a frame at offset 1 of the first block and at offset 3 of the second
            val joined = listOf(
                fragment(3, SameFrame(1)),
                fragment(2, SameFrame(0)),
            ).join()

            // then both deltas are measured from one past the previous frame
            assertThat(joined.frames).containsExactly(SameFrame(1), SameFrame(1))
        }

        @Test
        fun `leaves the frames after the first of a fragment alone`() {
            // given
            val joined = listOf(
                fragment(5, SameFrame(0), SameFrame(2)),
                fragment(1),
            ).join()

            // then only the first frame of a fragment needs patching
            assertThat(joined.frames).containsExactly(SameFrame(0), SameFrame(2))
        }

        @Test
        fun `drops a frame that lands on the offset the previous one already covers`() {
            // given a frame at the very end of the first fragment and at the start of the
            // second: both describe the same offset, which is what an empty loop body does
            val joined = listOf(
                fragment(3, SameFrame(3)),
                fragment(2, SameFrame(0)),
            ).join()

            // then the second is dropped rather than recorded with a negative delta
            assertThat(joined.frames).containsExactly(SameFrame(3))
        }

        @Test
        fun `re-picks the compact form when patching an extended same frame`() {
            // given
            val joined = listOf(
                fragment(2),
                fragment(1, SameFrameExtended(0)),
            ).join()

            // then an offset that now fits a byte goes back to the compact form
            assertThat(joined.frames).containsExactly(SameFrame(2))
        }

        @Test
        fun `patches an append frame`() {
            // given
            val joined = listOf(
                fragment(2),
                fragment(1, AppendFrame(0, listOf(IntegerVariableInfo))),
            ).join()

            // then
            assertThat(joined.frames)
                .containsExactly(AppendFrame(2, listOf(IntegerVariableInfo)))
        }

        @Test
        fun `patches a stack frame`() {
            // given
            val joined = listOf(
                fragment(2),
                fragment(1, SameLocals1StackItemFrameShort(0, IntegerVariableInfo)),
            ).join()

            // then
            assertThat(joined.frames)
                .containsExactly(SameLocals1StackItemFrameShort(2, IntegerVariableInfo))
        }

        @Test
        fun `re-picks the compact form when patching an extended stack frame`() {
            // given
            val joined = listOf(
                fragment(2),
                fragment(1, SameLocals1StackItemFrameExtended(0, IntegerVariableInfo)),
            ).join()

            // then, as with a same frame, an offset that fits a byte goes back to the tag
            assertThat(joined.frames)
                .containsExactly(SameLocals1StackItemFrameShort(2, IntegerVariableInfo))
        }

        @Test
        fun `patches a full frame`() {
            // given
            val frame = FullFrame(0, listOf(IntegerVariableInfo), listOf(ObjectVariableInfo(3)))
            val joined = listOf(fragment(2), fragment(1, frame)).join()

            // then
            assertThat(joined.frames).containsExactly(frame.copy(offsetDelta = 2))
        }

        @Test
        fun `keeps a fragment without frames from shifting the ones after it`() {
            // given
            val joined = listOf(
                fragment(2, SameFrame(0)),
                fragment(3),
                fragment(1, SameFrame(0)),
            ).join()

            // then the frameless fragment still advances the offset the last frame sees
            assertThat(joined.frames).containsExactly(SameFrame(0), SameFrame(4))
        }
    }

    @Nested
    inner class JoinedHandlers {

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
            // given a handler covering the whole of a fragment that lands at offset 5
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
        fun `moves handlers and frames by the same offsets`() {
            // given a fragment carrying both
            val body = Fragment(
                listOf(ByteArray(3)),
                listOf(SameLocals1StackItemFrameShort(1, IntegerVariableInfo)),
                listOf(ExceptionHandler(0, 1, 1, catchType = 0)),
                3,
            )
            val joined = listOf(fragment(2), body).join()

            // then the frame delta counts from the previous frame and the handler from zero
            assertThat(joined.frames)
                .containsExactly(SameLocals1StackItemFrameShort(3, IntegerVariableInfo))
            assertThat(joined.exceptionHandlers)
                .containsExactly(ExceptionHandler(2, 3, 3, catchType = 0))
        }
    }
}
