package com.alsaril.codegen

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ClassWriterTest {

    @Test
    fun `writes nothing for an empty serializer`() {
        assertThat(toBytes { }).isEmpty()
    }

    @Test
    fun `appends in call order`() {
        assertThat(toBytes { byte(1); short(2); byte(3) })
            .containsExactly(*bytesOf(0x01, 0x00, 0x02, 0x03))
    }

    @Nested
    inner class Widths {

        @Test
        fun `byte writes one byte`() {
            assertThat(toBytes { byte(0x7F) }).containsExactly(*bytesOf(0x7F))
            assertThat(toBytes { byte(0xFF) }).containsExactly(*bytesOf(0xFF))
        }

        @Test
        fun `short writes two big-endian bytes`() {
            assertThat(toBytes { short(0x0102) }).containsExactly(*bytesOf(0x01, 0x02))
            assertThat(toBytes { short(0xFFFF) }).containsExactly(*bytesOf(0xFF, 0xFF))
        }

        @Test
        fun `int writes four big-endian bytes`() {
            assertThat(toBytes { int(0x01020304) })
                .containsExactly(*bytesOf(0x01, 0x02, 0x03, 0x04))
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
    inner class Truncation {

        @Test
        fun `byte keeps only the low eight bits`() {
            assertThat(toBytes { byte(0x1FF) }).containsExactly(*bytesOf(0xFF))
        }

        /**
         * Nothing rejects an index past the constant pool limit, it is silently
         * truncated, so a pool with more than 65535 entries would emit a broken file.
         */
        @Test
        fun `short keeps only the low sixteen bits`() {
            assertThat(toBytes { short(0x10002) }).containsExactly(*bytesOf(0x00, 0x02))
            assertThat(toBytes { short(65536) }).containsExactly(*bytesOf(0x00, 0x00))
        }
    }
}
