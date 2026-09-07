package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.bytesOf
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
