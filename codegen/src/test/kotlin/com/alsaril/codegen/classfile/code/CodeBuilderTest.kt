package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.bytesOf
import com.alsaril.codegen.classfile.Fragment
import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.classfile.attributes.AppendFrame
import com.alsaril.codegen.classfile.attributes.FullFrame
import com.alsaril.codegen.classfile.attributes.SameFrame
import com.alsaril.codegen.classfile.attributes.SameFrameExtended
import com.alsaril.codegen.classfile.attributes.SameLocals1StackItemFrameShort
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.IntegerVariableInfo
import com.alsaril.codegen.classfile.attributes.StackMapFrame
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CodeBuilderTest {

    @Test
    fun `starts empty`() {
        // given
        val fragment = builder().build()

        // then
        assertThat(fragment.size).isZero()
        assertThat(fragment.frames).isEmpty()
    }

    @Test
    fun `reports the current offset as code is emitted`() {
        // given
        val builder = builder()

        // then
        assertThat(builder.loc()).isZero()
        builder.nop()
        assertThat(builder.loc()).isEqualTo(1)
        builder.goto(0)
        assertThat(builder.loc()).isEqualTo(4)
    }

    @Nested
    inner class MaxStack {

        @Test
        fun `asks for no stack until a body declares a depth`() {
            assertThat(builder().build().maxStack).isZero()
        }

        @Test
        fun `carries the declared depth on the fragment`() {
            // given
            val builder = builder().apply { maxStack(3) }

            // then
            assertThat(builder.build().maxStack).isEqualTo(3)
        }

        @Test
        fun `keeps the largest depth it is given, whichever order they arrive in`() {
            assertThat(builder().apply { maxStack(4); maxStack(2) }.build().maxStack).isEqualTo(4)
            assertThat(builder().apply { maxStack(2); maxStack(4) }.build().maxStack).isEqualTo(4)
        }
    }

    @Nested
    inner class Splicing {

        private fun piece(vararg bytes: Int) =
            Fragment(listOf(bytesOf(*bytes)), emptyList(), emptyList(), maxStack = 0, maxLocals = 0, size = bytes.size)

        private fun framed(size: Int, vararg frames: StackMapFrame) =
            Fragment(listOf(ByteArray(size)), frames.toList(), emptyList(), maxStack = 0, maxLocals = 0, size = size)

        private fun guarded(size: Int, vararg handlers: ExceptionHandler) =
            Fragment(listOf(ByteArray(size)), emptyList(), handlers.toList(), maxStack = 0, maxLocals = 0, size = size)

        @Test
        fun `appends the bytes where the builder had got to`() {
            assertThat(bytecode { nop(); fragment(piece(0x01, 0x02)) })
                .containsExactly(*bytesOf(0x00, 0x01, 0x02))
        }

        @Test
        fun `appends several fragments in the order they were spliced`() {
            assertThat(bytecode { fragment(piece(0x01)); fragment(piece(0x02, 0x03)) })
                .containsExactly(*bytesOf(0x01, 0x02, 0x03))
        }

        @Test
        fun `takes a fragment with nothing in it`() {
            assertThat(bytecode { nop(); fragment(piece()); nop() })
                .containsExactly(*bytesOf(0x00, 0x00))
        }

        @Test
        fun `keeps emitting after a fragment`() {
            assertThat(bytecode { fragment(piece(0x01)); nop() })
                .containsExactly(*bytesOf(0x01, 0x00))
        }

        @Test
        fun `rewrites the first frame against the builder's position`() {
            // the frame sits at offset 1 of a fragment spliced in at offset 3
            assertThat(frames { repeat(3) { nop() }; fragment(framed(3, SameFrame(1))) })
                .containsExactly(SameFrame(4))
        }

        @Test
        fun `rewrites the first frame whatever kind it is`() {
            // given a fragment spliced in at 1, whose own frame sits at offset 1
            val locals = listOf(IntegerVariableInfo)

            // then every frame shape carries its contents across the move
            assertThat(frames { nop(); fragment(framed(2, SameFrameExtended(1))) })
                .containsExactly(SameFrame(2))
            assertThat(frames { nop(); fragment(framed(2, AppendFrame(1, locals))) })
                .containsExactly(AppendFrame(2, locals))
            assertThat(frames { nop(); fragment(framed(2, FullFrame(1, locals, emptyList()))) })
                .containsExactly(FullFrame(2, locals, emptyList()))
            assertThat(frames {
                nop()
                fragment(framed(2, SameLocals1StackItemFrameShort(1, IntegerVariableInfo)))
            }).containsExactly(SameLocals1StackItemFrameShort(2, IntegerVariableInfo))
        }

        @Test
        fun `leaves the frames after the first alone`() {
            assertThat(frames { fragment(framed(5, SameFrame(0), SameFrame(2))) })
                .containsExactly(SameFrame(0), SameFrame(2))
        }

        @Test
        fun `measures a frame already in the builder before the one it splices`() {
            // the builder's frame is at offset 1, and the fragment's own frame at offset 1
            // of a fragment spliced in at 1, so the second sits at 2 and is one past the first
            assertThat(frames { nop(); frameSame(); fragment(framed(3, SameFrame(1))) })
                .containsExactly(SameFrame(1), SameFrame(0))
        }

        @Test
        fun `drops a frame that lands on the offset the previous one already covers`() {
            assertThat(frames { nop(); frameSame(); fragment(framed(2, SameFrame(0))) })
                .containsExactly(SameFrame(1))
        }

        @Test
        fun `slides an exception handler by where the fragment landed`() {
            assertThat(handlers { repeat(4) { nop() }; fragment(guarded(3, ExceptionHandler(0, 2, 2, catchType = 7))) })
                .containsExactly(ExceptionHandler(4, 6, 6, catchType = 7))
        }

        @Test
        fun `slides every handler of the fragment`() {
            val spliced = guarded(
                4,
                ExceptionHandler(0, 1, 1, catchType = 0),
                ExceptionHandler(2, 3, 3, catchType = 0),
            )

            assertThat(handlers { nop(); fragment(spliced) }).containsExactly(
                ExceptionHandler(1, 2, 2, catchType = 0),
                ExceptionHandler(3, 4, 4, catchType = 0),
            )
        }

        @Test
        fun `refuses a fragment whose jump is still waiting for a target`() {
            // given a fragment built with its jump target still unknown
            val source = builder()
            source.goto()
            source.nop()
            val open = source.build()

            // then
            assertThatIllegalArgumentException()
                .isThrownBy { builder().apply { fragment(open) } }
                .withMessageContaining("patch before splicing")
        }

        @Test
        fun `takes the same fragment once its jump has been patched`() {
            // given
            val source = builder()
            val jump = source.goto()
            source.nop()
            val open = source.build()

            // when the target is supplied before the splice rather than after
            jump(9)

            // then
            assertThat(builder().apply { fragment(open) }.build().bytecode())
                .containsExactly(*bytesOf(0xA7, 0x00, 0x09, 0x00))
        }

        @Test
        fun `raises the stack requirement to what the fragment needs`() {
            val deep = Fragment(listOf(ByteArray(1)), emptyList(), emptyList(), maxStack = 3, maxLocals = 0, size = 1)

            assertThat(builder().apply { fragment(deep) }.build().maxStack).isEqualTo(3)
        }

        @Test
        fun `keeps a larger requirement the builder already had`() {
            val shallow = Fragment(listOf(ByteArray(1)), emptyList(), emptyList(), maxStack = 1, maxLocals = 0, size = 1)

            assertThat(builder().apply { maxStack(5); fragment(shallow) }.build().maxStack)
                .isEqualTo(5)
        }
    }

    @Nested
    inner class OperandWidths {

        @Test
        fun `u1 takes an unsigned byte`() {
            assertThatNoException().isThrownBy { bytecode { u1(0); u1(0xff) } }

            assertThatIllegalArgumentException()
                .isThrownBy { builder().u1(0x100) }
                .withMessageContaining("does not fit a u1")
        }

        @Test
        fun `s1 takes a signed byte`() {
            assertThatNoException().isThrownBy { bytecode { s1(-128); s1(127) } }

            assertThatIllegalArgumentException()
                .isThrownBy { builder().s1(128) }
                .withMessageContaining("does not fit an s1")

            assertThatIllegalArgumentException().isThrownBy { builder().s1(-129) }
        }

        @Test
        fun `u2 takes an unsigned short`() {
            assertThat(bytecode { u2(0xCAFE) }).containsExactly(*bytesOf(0xCA, 0xFE))

            assertThatIllegalArgumentException()
                .isThrownBy { builder().u2(0x10000) }
                .withMessageContaining("does not fit a u2")
        }

        @Test
        fun `s2 takes a signed short`() {
            assertThat(bytecode { s2(-2) }).containsExactly(*bytesOf(0xFF, 0xFE))

            assertThatIllegalArgumentException()
                .isThrownBy { builder().s2(32_768) }
                .withMessageContaining("does not fit an s2")
        }

        // 200 is a fine u1 and not an s1, -1 the other way round; one range covering
        // both signednesses would accept all four of these
        @Test
        fun `does not accept a value that fits only the other signedness`() {
            assertThatIllegalArgumentException().isThrownBy { builder().s1(200) }
            assertThatIllegalArgumentException().isThrownBy { builder().u1(-1) }
            assertThatIllegalArgumentException().isThrownBy { builder().s2(0xffff) }
            assertThatIllegalArgumentException().isThrownBy { builder().u2(-1) }
        }
    }

    /**
     * Building freezes the code into an array the fragment holds, but a jump whose
     * target is only known later still has to be able to patch it. That deferred patch
     * is what lets a loop prefix be emitted as its own fragment.
     */
    @Nested
    inner class Freezing {

        @Test
        fun `refuses to build twice`() {
            // given
            val builder = builder().apply { nop() }
            builder.build()

            // then
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { builder.build() }
        }

        @Test
        fun `refuses to emit more code once built`() {
            // given
            val builder = builder().apply { nop() }
            builder.build()

            // then
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { builder.nop() }
        }

        @Test
        fun `patches a jump raised before building`() {
            // given
            val builder = builder()
            val jump = builder.goto()
            builder.nop()
            jump(builder.loc())

            // then
            assertThat(builder.build().bytecode())
                .containsExactly(*bytesOf(0xA7, 0x00, 0x04, 0x00))
        }

        @Test
        fun `patches a jump left open until after building`() {
            // given
            val builder = builder()
            val jump = builder.goto()
            builder.nop()
            val fragment = builder.build()

            // when the target only becomes known after the fragment exists
            jump(builder.loc())

            // then the patch reaches the bytes the fragment already handed out
            assertThat(fragment.bytecode())
                .containsExactly(*bytesOf(0xA7, 0x00, 0x04, 0x00))
        }

        @Test
        fun `still reports offsets after building`() {
            // given
            val builder = builder().apply { nop(); nop() }
            builder.build()

            // then
            assertThatNoException().isThrownBy { builder.loc() }
            assertThat(builder.loc()).isEqualTo(2)
        }
    }
}
