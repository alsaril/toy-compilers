package com.alsaril.codegen

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ClassWriterTest {

    @Test
    fun `writes nothing for an empty serializer`() {
        assertThat(toBytes { }).isEmpty()
    }

    @Test
    fun `appends in call order`() {
        assertThat(toBytes { u1(1); u2(2); u1(3) })
            .containsExactly(*bytesOf(0x01, 0x00, 0x02, 0x03))
    }

    @Nested
    inner class Widths {

        @Test
        fun `byte writes one byte`() {
            assertThat(toBytes { u1(0x7F) }).containsExactly(*bytesOf(0x7F))
            assertThat(toBytes { u1(0xFF) }).containsExactly(*bytesOf(0xFF))
        }

        @Test
        fun `short writes two big-endian bytes`() {
            assertThat(toBytes { u2(0x0102) }).containsExactly(*bytesOf(0x01, 0x02))
            assertThat(toBytes { u2(0xFFFF) }).containsExactly(*bytesOf(0xFF, 0xFF))
        }

        @Test
        fun `s1 writes one byte`() {
            assertThat(toBytes { s1(0x7F) }).containsExactly(*bytesOf(0x7F))
            assertThat(toBytes { s1(0) }).containsExactly(*bytesOf(0x00))
        }

        @Test
        fun `s1 writes a negative value in two's complement`() {
            assertThat(toBytes { s1(-1) }).containsExactly(*bytesOf(0xFF))
            assertThat(toBytes { s1(-128) }).containsExactly(*bytesOf(0x80))
        }

        @Test
        fun `s2 writes two big-endian bytes`() {
            assertThat(toBytes { s2(0x0102) }).containsExactly(*bytesOf(0x01, 0x02))
            assertThat(toBytes { s2(0x7FFF) }).containsExactly(*bytesOf(0x7F, 0xFF))
        }

        @Test
        fun `s2 writes a negative value in two's complement`() {
            assertThat(toBytes { s2(-1) }).containsExactly(*bytesOf(0xFF, 0xFF))
            assertThat(toBytes { s2(-32768) }).containsExactly(*bytesOf(0x80, 0x00))
        }

        @Test
        fun `int writes four big-endian bytes`() {
            assertThat(toBytes { int(0x01020304) })
                .containsExactly(*bytesOf(0x01, 0x02, 0x03, 0x04))
        }

        @Test
        fun `float writes its ieee 754 bit pattern`() {
            assertThat(toBytes { float(1.5f) })
                .containsExactly(*bytesOf(0x3F, 0xC0, 0x00, 0x00))
        }

        @Test
        fun `long writes eight big-endian bytes`() {
            assertThat(toBytes { long(0x0102030405060708L) })
                .containsExactly(*bytesOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08))
        }

        @Test
        fun `double writes its ieee 754 bit pattern`() {
            assertThat(toBytes { double(1.5) })
                .containsExactly(*bytesOf(0x3F, 0xF8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        }

        @Test
        fun `bytes writes the array unchanged`() {
            assertThat(toBytes { bytes(bytesOf(0xCA, 0xFE)) })
                .containsExactly(*bytesOf(0xCA, 0xFE))
        }

        @Test
        fun `utf8 writes a length prefix and modified utf8`() {
            assertThat(toBytes { utf8("hi") })
                .containsExactly(*bytesOf(0x00, 0x02, 0x68, 0x69))
        }
    }

    @Nested
    inner class Overflow {

        @Test
        fun `byte rejects a value wider than a u1`() {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { toBytes { u1(0x1FF) } }
                .withMessageContaining("does not fit a u1")

            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { toBytes { u1(-1) } }
        }

        @Test
        fun `short rejects a value wider than a u2`() {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { toBytes { u2(65536) } }
                .withMessageContaining("does not fit a u2")

            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { toBytes { u2(-1) } }
        }

        @Test
        fun `s1 rejects a value wider than an s1`() {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { toBytes { s1(128) } }
                .withMessageContaining("does not fit an s1")

            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { toBytes { s1(-129) } }
        }

        @Test
        fun `s2 rejects a value wider than an s2`() {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { toBytes { s2(32768) } }
                .withMessageContaining("does not fit an s2")

            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { toBytes { s2(-32769) } }
        }

        // 200 is a fine u1 and not an s1, -1 the other way round; one range covering
        // both signednesses would accept all four of these
        @Test
        fun `does not accept a value that fits only the other signedness`() {
            assertThatIllegalArgumentException().isThrownBy { toBytes { s1(200) } }
            assertThatIllegalArgumentException().isThrownBy { toBytes { u1(-1) } }
            assertThatIllegalArgumentException().isThrownBy { toBytes { s2(0xFFFF) } }
            assertThatIllegalArgumentException().isThrownBy { toBytes { u2(-1) } }
        }

        @Test
        fun `accepts the widest value each still holds`() {
            assertThat(toBytes { u1(0xFF) }).containsExactly(*bytesOf(0xFF))
            assertThat(toBytes { u2(0xFFFF) }).containsExactly(*bytesOf(0xFF, 0xFF))
            assertThat(toBytes { s1(127) }).containsExactly(*bytesOf(0x7F))
            assertThat(toBytes { s1(-128) }).containsExactly(*bytesOf(0x80))
            assertThat(toBytes { s2(32767) }).containsExactly(*bytesOf(0x7F, 0xFF))
            assertThat(toBytes { s2(-32768) }).containsExactly(*bytesOf(0x80, 0x00))
        }
    }
}
