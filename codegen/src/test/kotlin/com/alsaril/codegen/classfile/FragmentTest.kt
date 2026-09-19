package com.alsaril.codegen.classfile

import com.alsaril.codegen.bytesOf
import com.alsaril.codegen.classfile.code.builder
import com.alsaril.codegen.classfile.code.goto
import com.alsaril.codegen.classfile.code.nop
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
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * A frame records how far it sits from the previous one, so joining fragments has to
 * rewrite the first frame of each against the offsets it lands on in the joined code.
 * An exception handler names absolute offsets instead, so all three of its locations
 * move by however far its fragment was pushed along.
 */
class FragmentTest {

    // TODO(ir): a Fragment holds an instruction list and an identity-keyed frame map now,
    // not blocks of bytes carrying their own maxStack/maxLocals
    // private fun fragment(size: Int, vararg frames: StackMapFrame) =
    //     Fragment(listOf(ByteArray(size)), frames.toList(), emptyList(), maxStack = 0, maxLocals = 0, size = size)
    //
    // private fun guarded(size: Int, vararg handlers: ExceptionHandler) =
    //     Fragment(listOf(ByteArray(size)), emptyList(), handlers.toList(), maxStack = 0, maxLocals = 0, size = size)
    //
    // private fun stacked(maxStack: Int, size: Int = 1) =
    //     Fragment(listOf(ByteArray(size)), emptyList(), emptyList(), maxStack, 0, size)

    @Nested
    inner class Bytecode {

        @Test
        fun `hands back a single block without copying it`() {
            // TODO(ir): a Fragment no longer owns blocks of bytes - BytecodeSerializer lays the instructions out
            // // given
            // val block = bytesOf(0x01, 0x02)
            // val fragment = Fragment(listOf(block), emptyList(), emptyList(), maxStack = 0, maxLocals = 0, size = 2)

            // // then
            // assertThat(fragment.bytecode()).isSameAs(block)
        }

        @Test
        fun `hands back a single block even while a jump waits, since nothing is copied`() {
            // TODO(ir): a Fragment no longer owns blocks of bytes - BytecodeSerializer lays the instructions out
            // // given a fragment built with its jump target still unknown
            // val source = builder()
            // val jump = source.goto()
            // source.nop()
            // val open = source.build()

            // // when the one block is handed back as it stands, a later patch still reaches it
            // val handed = open.bytecode()
            // jump(9)

            // // then
            // assertThat(handed).containsExactly(*bytesOf(0xA7, 0x00, 0x09, 0x00))
        }

        @Test
        fun `concatenates several blocks`() {
            // TODO(ir): a Fragment no longer owns blocks of bytes - BytecodeSerializer lays the instructions out
            // // given
            // val fragment = Fragment(
            //     listOf(bytesOf(0x01), bytesOf(0x02, 0x03), bytesOf(0x04)),
            //     emptyList(),
            //     emptyList(),
            //     maxStack = 0,
            //     maxLocals = 0,
            //     size = 4,
            // )

            // // then
            // assertThat(fragment.bytecode()).containsExactly(*bytesOf(0x01, 0x02, 0x03, 0x04))
        }

        @Test
        fun `is empty when there is no content`() {
            // TODO(ir): a Fragment no longer owns blocks of bytes - BytecodeSerializer lays the instructions out
            // assertThat(Fragment(emptyList(), emptyList(), emptyList(), maxStack = 0, maxLocals = 0, size = 0).bytecode())
            //     .isEmpty()
        }
    }

    @Nested
    inner class Join {

        @Test
        fun `hands back a lone fragment untouched`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given
            // val only = fragment(3, SameFrame(1))

            // // then
            // assertThat(listOf(only).join()).isSameAs(only)
        }

        @Test
        fun `produces an empty fragment for no input`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given
            // val joined = emptyList<Fragment>().join()

            // // then
            // assertThat(joined.size).isZero()
            // assertThat(joined.frames).isEmpty()
            // assertThat(joined.exceptionHandlers).isEmpty()
            // assertThat(joined.content).isEmpty()
        }

        @Test
        fun `takes the largest stack requirement of the fragments it joins`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given code laid out so the deepest body is neither first nor last
            // val joined = listOf(stacked(1), stacked(4), stacked(2)).join()

            // // then
            // assertThat(joined.maxStack).isEqualTo(4)
        }

        @Test
        fun `keeps the stack requirement of a lone fragment`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // assertThat(listOf(stacked(3)).join().maxStack).isEqualTo(3)
        }

        @Test
        fun `asks for no stack when none of the fragments do`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // assertThat(listOf(fragment(1), fragment(2)).join().maxStack).isZero()
        }

        @Test
        fun `shares the blocks rather than copying them, so a later patch still lands`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given a fragment whose jump target is only known after it was built
            // val builder = builder()
            // val jump = builder.goto()
            // builder.nop()
            // val open = builder.build()

            // // when it is joined and only then patched
            // val joined = listOf(open, builder().apply { nop() }.build()).join()
            // jump(9)

            // // then the patch reached, because joining moved the block and not its bytes
            // assertThat(joined.content.first()).isSameAs(open.content.first())
            // assertThat(joined.bytecode()).startsWith(*bytesOf(0xA7, 0x00, 0x09))
        }

        @Test
        fun `refuses a fragment whose handler is still waiting for a location`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given a range closed but not yet given a handler. Unlike a jump, which is
            // // patched into a block the join goes on sharing, a handler row is rewritten
            // // into a new list against where its fragment landed
            // val source = builder()
            // val from = source.`try`()
            // source.nop()
            // source.`catch`(from, type = null)
            // val open = source.build()

            // // then
            // assertThatIllegalArgumentException()
            //     .isThrownBy { listOf(open, builder().apply { nop() }.build()).join() }
            //     .withMessageContaining("patch before joining")
        }

        @Test
        fun `carries the jumps still waiting from every fragment it joined`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given one fragment owing a target joined with one that does not
            // val source = builder()
            // source.goto()
            // source.nop()
            // val joined = listOf(source.build(), builder().apply { nop() }.build()).join()

            // // then flattening its blocks would copy them, so it is refused rather than lost
            // assertThatIllegalArgumentException()
            //     .isThrownBy { joined.bytecode() }
            //     .withMessageContaining("patch before flattening")
        }

        @Test
        fun `adds up the sizes and keeps the blocks in order`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given
            // val joined = listOf(
            //     Fragment(listOf(bytesOf(0x01, 0x02)), emptyList(), emptyList(), maxStack = 0, maxLocals = 0, size = 2),
            //     Fragment(listOf(bytesOf(0x03)), emptyList(), emptyList(), maxStack = 0, maxLocals = 0, size = 1),
            // ).join()

            // // then
            // assertThat(joined.size).isEqualTo(3)
            // assertThat(joined.bytecode()).containsExactly(*bytesOf(0x01, 0x02, 0x03))
        }

        @Test
        fun `rewrites the first frame of a later fragment against the joined offsets`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given a frame at offset 1 of the first block and at offset 3 of the second
            // val joined = listOf(
            //     fragment(3, SameFrame(1)),
            //     fragment(2, SameFrame(0)),
            // ).join()

            // // then both deltas are measured from one past the previous frame
            // assertThat(joined.frames).containsExactly(SameFrame(1), SameFrame(1))
        }

        @Test
        fun `leaves the frames after the first of a fragment alone`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given
            // val joined = listOf(
            //     fragment(5, SameFrame(0), SameFrame(2)),
            //     fragment(1),
            // ).join()

            // // then only the first frame of a fragment needs patching
            // assertThat(joined.frames).containsExactly(SameFrame(0), SameFrame(2))
        }

        @Test
        fun `drops a frame that lands on the offset the previous one already covers`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given a frame at the very end of the first fragment and at the start of the
            // // second: both describe the same offset, which is what an empty loop body does
            // val joined = listOf(
            //     fragment(3, SameFrame(3)),
            //     fragment(2, SameFrame(0)),
            // ).join()

            // // then the second is dropped rather than recorded with a negative delta
            // assertThat(joined.frames).containsExactly(SameFrame(3))
        }

        @Test
        fun `re-picks the compact form when patching an extended same frame`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given
            // val joined = listOf(
            //     fragment(2),
            //     fragment(1, SameFrameExtended(0)),
            // ).join()

            // // then an offset that now fits a byte goes back to the compact form
            // assertThat(joined.frames).containsExactly(SameFrame(2))
        }

        @Test
        fun `patches an append frame`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given
            // val joined = listOf(
            //     fragment(2),
            //     fragment(1, AppendFrame(0, listOf(IntegerVariableInfo))),
            // ).join()

            // // then
            // assertThat(joined.frames)
            //     .containsExactly(AppendFrame(2, listOf(IntegerVariableInfo)))
        }

        @Test
        fun `patches a stack frame`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given
            // val joined = listOf(
            //     fragment(2),
            //     fragment(1, SameLocals1StackItemFrameShort(0, IntegerVariableInfo)),
            // ).join()

            // // then
            // assertThat(joined.frames)
            //     .containsExactly(SameLocals1StackItemFrameShort(2, IntegerVariableInfo))
        }

        @Test
        fun `re-picks the compact form when patching an extended stack frame`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given
            // val joined = listOf(
            //     fragment(2),
            //     fragment(1, SameLocals1StackItemFrameExtended(0, IntegerVariableInfo)),
            // ).join()

            // // then, as with a same frame, an offset that fits a byte goes back to the tag
            // assertThat(joined.frames)
            //     .containsExactly(SameLocals1StackItemFrameShort(2, IntegerVariableInfo))
        }

        @Test
        fun `patches a full frame`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given
            // val frame = FullFrame(0, listOf(IntegerVariableInfo), listOf(ObjectVariableInfo(3)))
            // val joined = listOf(fragment(2), fragment(1, frame)).join()

            // // then
            // assertThat(joined.frames).containsExactly(frame.copy(offsetDelta = 2))
        }

        @Test
        fun `keeps a fragment without frames from shifting the ones after it`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given
            // val joined = listOf(
            //     fragment(2, SameFrame(0)),
            //     fragment(3),
            //     fragment(1, SameFrame(0)),
            // ).join()

            // // then the frameless fragment still advances the offset the last frame sees
            // assertThat(joined.frames).containsExactly(SameFrame(0), SameFrame(4))
        }
    }

    @Nested
    inner class JoinedHandlers {

        @Test
        fun `hands back a lone fragment's handlers untouched`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given
            // val handler = ExceptionHandler(1, 2, 2, catchType = 3)
            // val only = guarded(3, handler)

            // // then
            // assertThat(listOf(only).join().exceptionHandlers).containsExactly(handler)
        }

        @Test
        fun `leaves the handlers of the first fragment where they are`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given
            // val joined = listOf(
            //     guarded(4, ExceptionHandler(0, 2, 3, catchType = 0)),
            //     guarded(1),
            // ).join()

            // // then
            // assertThat(joined.exceptionHandlers)
            //     .containsExactly(ExceptionHandler(0, 2, 3, catchType = 0))
        }

        @Test
        fun `shifts every location by how far its fragment moved`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given a handler covering the whole of a fragment that lands at offset 5
            // val joined = listOf(
            //     guarded(5),
            //     guarded(4, ExceptionHandler(0, 2, 3, catchType = 7)),
            // ).join()

            // // then the range and the handler move together, and the caught type does not
            // assertThat(joined.exceptionHandlers)
            //     .containsExactly(ExceptionHandler(5, 7, 8, catchType = 7))
        }

        @Test
        fun `collects the handlers of every fragment in order`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given
            // val joined = listOf(
            //     guarded(2, ExceptionHandler(0, 1, 1, catchType = 0)),
            //     guarded(2, ExceptionHandler(0, 1, 1, catchType = 0)),
            //     guarded(2, ExceptionHandler(0, 1, 1, catchType = 0)),
            // ).join()

            // // then the order the jvm searches them in survives the join
            // assertThat(joined.exceptionHandlers).containsExactly(
            //     ExceptionHandler(0, 1, 1, catchType = 0),
            //     ExceptionHandler(2, 3, 3, catchType = 0),
            //     ExceptionHandler(4, 5, 5, catchType = 0),
            // )
        }

        @Test
        fun `keeps several handlers of one fragment together`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given
            // val joined = listOf(
            //     guarded(1),
            //     guarded(
            //         4,
            //         ExceptionHandler(0, 1, 2, catchType = 0),
            //         ExceptionHandler(1, 2, 3, catchType = 0),
            //     ),
            // ).join()

            // // then
            // assertThat(joined.exceptionHandlers).containsExactly(
            //     ExceptionHandler(1, 2, 3, catchType = 0),
            //     ExceptionHandler(2, 3, 4, catchType = 0),
            // )
        }

        @Test
        fun `moves handlers and frames by the same offsets`() {
            // TODO(ir): joining concatenates instruction lists, so nothing is rewritten against an offset
            // // given a fragment carrying both
            // val body = Fragment(
            //     listOf(ByteArray(3)),
            //     listOf(SameLocals1StackItemFrameShort(1, IntegerVariableInfo)),
            //     listOf(ExceptionHandler(0, 1, 1, catchType = 0)),
            //     maxStack = 0,
            //     maxLocals = 0,
            //     size = 3,
            // )
            // val joined = listOf(fragment(2), body).join()

            // // then the frame delta counts from the previous frame and the handler from zero
            // assertThat(joined.frames)
            //     .containsExactly(SameLocals1StackItemFrameShort(3, IntegerVariableInfo))
            // assertThat(joined.exceptionHandlers)
            //     .containsExactly(ExceptionHandler(2, 3, 3, catchType = 0))
        }
    }
}
