package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.bytesOf
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Opcode values are the ones listed in JVMS 6.5. */
class OpcodesTest {

    @Test
    fun `writes the single byte instructions`() {
        assertThat(bytecode { nop() }).containsExactly(*bytesOf(0x00))
        assertThat(bytecode { aconst_null() }).containsExactly(*bytesOf(0x01))
        assertThat(bytecode { baload() }).containsExactly(*bytesOf(0x33))
        assertThat(bytecode { bastore() }).containsExactly(*bytesOf(0x54))
        assertThat(bytecode { dup() }).containsExactly(*bytesOf(0x59))
        assertThat(bytecode { dup2() }).containsExactly(*bytesOf(0x5C))
        assertThat(bytecode { dup_x2() }).containsExactly(*bytesOf(0x5B))
        assertThat(bytecode { iaload() }).containsExactly(*bytesOf(0x2E))
        assertThat(bytecode { iastore() }).containsExactly(*bytesOf(0x4F))
        assertThat(bytecode { iadd() }).containsExactly(*bytesOf(0x60))
        assertThat(bytecode { isub() }).containsExactly(*bytesOf(0x64))
        assertThat(bytecode { ireturn() }).containsExactly(*bytesOf(0xAC))
        assertThat(bytecode { freturn() }).containsExactly(*bytesOf(0xAE))
        assertThat(bytecode { `return`() }).containsExactly(*bytesOf(0xB1))
        assertThat(bytecode { athrow() }).containsExactly(*bytesOf(0xBF))
    }

    @Nested
    inner class Fconst {

        @Test
        fun `writes the compact float constants`() {
            assertThat(bytecode { fconst(0) }).containsExactly(*bytesOf(0x0B))
            assertThat(bytecode { fconst(1) }).containsExactly(*bytesOf(0x0C))
            assertThat(bytecode { fconst(2) }).containsExactly(*bytesOf(0x0D))
        }

        @Test
        fun `rejects a float constant that needs a constant pool entry`() {
            assertThatIllegalArgumentException().isThrownBy { bytecode { fconst(3) } }
            assertThatIllegalArgumentException().isThrownBy { bytecode { fconst(-1) } }
        }
    }

    @Test
    fun `appends instructions in order`() {
        assertThat(bytecode { iadd(); isub(); `return`() })
            .containsExactly(*bytesOf(0x60, 0x64, 0xB1))
    }

    @Nested
    inner class Iconst {

        @Test
        fun `uses the compact form from minus one to five`() {
            assertThat(bytecode { iconst(-1) }).containsExactly(*bytesOf(0x02))
            assertThat(bytecode { iconst(0) }).containsExactly(*bytesOf(0x03))
            assertThat(bytecode { iconst(5) }).containsExactly(*bytesOf(0x08))
        }

        @Test
        fun `falls back to bipush outside the compact range`() {
            assertThat(bytecode { iconst(6) }).containsExactly(*bytesOf(0x10, 0x06))
            assertThat(bytecode { iconst(-2) }).containsExactly(*bytesOf(0x10, 0xFE))
            assertThat(bytecode { iconst(127) }).containsExactly(*bytesOf(0x10, 0x7F))
            assertThat(bytecode { iconst(-128) }).containsExactly(*bytesOf(0x10, 0x80))
        }

        @Test
        fun `falls back to sipush outside the byte range`() {
            assertThat(bytecode { iconst(128) }).containsExactly(*bytesOf(0x11, 0x00, 0x80))
            assertThat(bytecode { iconst(-129) }).containsExactly(*bytesOf(0x11, 0xFF, 0x7F))
            assertThat(bytecode { iconst(32767) }).containsExactly(*bytesOf(0x11, 0x7F, 0xFF))
            assertThat(bytecode { iconst(-32768) }).containsExactly(*bytesOf(0x11, 0x80, 0x00))
        }

        @Test
        fun `rejects a value that needs a constant pool entry`() {
            assertThatIllegalArgumentException().isThrownBy { bytecode { iconst(32768) } }
            assertThatIllegalArgumentException().isThrownBy { bytecode { iconst(-32769) } }
        }
    }

    @Nested
    inner class Ldc {

        @Test
        fun `uses the single byte form for a low pool index`() {
            assertThat(bytecode { ldc(DataPointer(1)) }).containsExactly(*bytesOf(0x12, 0x01))
            assertThat(bytecode { ldc(DataPointer(255)) }).containsExactly(*bytesOf(0x12, 0xFF))
        }

        @Test
        fun `switches to the wide form past a single byte index`() {
            assertThat(bytecode { ldc(DataPointer(256)) })
                .containsExactly(*bytesOf(0x13, 0x01, 0x00))
            assertThat(bytecode { ldc(DataPointer(65535)) })
                .containsExactly(*bytesOf(0x13, 0xFF, 0xFF))
        }
    }

    @Nested
    inner class LoadsAndStores {

        @Test
        fun `uses the indexed opcodes for the first four slots`() {
            assertThat(bytecode { iload(0) }).containsExactly(*bytesOf(0x1A))
            assertThat(bytecode { iload(3) }).containsExactly(*bytesOf(0x1D))
            assertThat(bytecode { aload(0) }).containsExactly(*bytesOf(0x2A))
            assertThat(bytecode { aload(3) }).containsExactly(*bytesOf(0x2D))
            assertThat(bytecode { istore(0) }).containsExactly(*bytesOf(0x3B))
            assertThat(bytecode { istore(3) }).containsExactly(*bytesOf(0x3E))
            assertThat(bytecode { astore(0) }).containsExactly(*bytesOf(0x4B))
            assertThat(bytecode { astore(3) }).containsExactly(*bytesOf(0x4E))
        }

        @Test
        fun `switches to the operand form from the fifth slot`() {
            assertThat(bytecode { iload(4) }).containsExactly(*bytesOf(0x15, 0x04))
            assertThat(bytecode { aload(4) }).containsExactly(*bytesOf(0x19, 0x04))
            assertThat(bytecode { istore(4) }).containsExactly(*bytesOf(0x36, 0x04))
            assertThat(bytecode { astore(4) }).containsExactly(*bytesOf(0x3A, 0x04))
        }

        @Test
        fun `uses the highest single byte index`() {
            assertThat(bytecode { iload(255) }).containsExactly(*bytesOf(0x15, 0xFF))
        }

        @Test
        fun `does not support an index beyond a single byte`() {
            assertThatExceptionOfType(NotImplementedError::class.java)
                .isThrownBy { bytecode { iload(256) } }
            assertThatExceptionOfType(NotImplementedError::class.java)
                .isThrownBy { bytecode { astore(256) } }
        }
    }

    @Nested
    inner class Iinc {

        @Test
        fun `uses the compact form for small index and constant`() {
            assertThat(bytecode { iinc(1, 2) }).containsExactly(*bytesOf(0x84, 0x01, 0x02))
            assertThat(bytecode { iinc(255, 127) }).containsExactly(*bytesOf(0x84, 0xFF, 0x7F))
        }

        @Test
        fun `writes a negative constant in two's complement`() {
            assertThat(bytecode { iinc(0, -1) }).containsExactly(*bytesOf(0x84, 0x00, 0xFF))
        }

        @Test
        fun `switches to the wide form for a large index`() {
            assertThat(bytecode { iinc(256, 1) })
                .containsExactly(*bytesOf(0xC4, 0x84, 0x01, 0x00, 0x00, 0x01))
        }

        @Test
        fun `switches to the wide form for a large constant`() {
            assertThat(bytecode { iinc(0, 128) })
                .containsExactly(*bytesOf(0xC4, 0x84, 0x00, 0x00, 0x00, 0x80))
            assertThat(bytecode { iinc(0, -32768) })
                .containsExactly(*bytesOf(0xC4, 0x84, 0x00, 0x00, 0x80, 0x00))
        }

        @Test
        fun `rejects a constant beyond the wide form`() {
            assertThatIllegalArgumentException().isThrownBy { bytecode { iinc(0, 32768) } }
            assertThatIllegalArgumentException().isThrownBy { bytecode { iinc(0, -32769) } }
        }
    }
}
