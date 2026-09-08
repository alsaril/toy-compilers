package com.alsaril.codegen.constantpool

import com.alsaril.codegen.bytesOf
import com.alsaril.codegen.serialized
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.UTFDataFormatException

/**
 * Byte-level expectations come from the JVMS 4.4 constant pool layout, so the
 * expected arrays are spelled out literally rather than produced by a second
 * implementation of the same encoding.
 */
class ConstantPoolSerializationTest {

    @Nested
    inner class Utf8 {

        @Test
        fun `writes tag, byte length and the ascii bytes`() {
            assertThat(ConstantUtf8Info("hi").serialized())
                .containsExactly(*bytesOf(0x01, 0x00, 0x02, 0x68, 0x69))
        }

        @Test
        fun `writes an empty string as a zero length`() {
            assertThat(ConstantUtf8Info("").serialized())
                .containsExactly(*bytesOf(0x01, 0x00, 0x00))
        }

        @Test
        fun `writes a non-ascii character as multi-byte utf8`() {
            // é is U+00E9, two bytes, so the length counts bytes and not characters
            assertThat(ConstantUtf8Info("é").serialized())
                .containsExactly(*bytesOf(0x01, 0x00, 0x02, 0xC3, 0xA9))
        }

        @Test
        fun `writes NUL as two bytes in modified utf8`() {
            // modified UTF-8 encodes U+0000 as C0 80 so it never appears as a 0 byte
            assertThat(ConstantUtf8Info("\u0000").serialized())
                .containsExactly(*bytesOf(0x01, 0x00, 0x02, 0xC0, 0x80))
        }

        @Test
        fun `writes a supplementary character as a surrogate pair`() {
            // U+1F600 becomes two three-byte surrogates, not a single four-byte sequence
            assertThat(ConstantUtf8Info("😀").serialized())
                .containsExactly(*bytesOf(0x01, 0x00, 0x06, 0xED, 0xA0, 0xBD, 0xED, 0xB8, 0x80))
        }

        @Test
        fun `rejects a value whose encoded form exceeds the two-byte length`() {
            assertThatExceptionOfType(UTFDataFormatException::class.java)
                .isThrownBy { ConstantUtf8Info("x".repeat(65536)).serialized() }
        }
    }

    @Nested
    inner class Numbers {

        @Test
        fun `writes an integer as four big-endian bytes`() {
            assertThat(ConstantIntegerInfo(0x01020304).serialized())
                .containsExactly(*bytesOf(0x03, 0x01, 0x02, 0x03, 0x04))
        }

        @Test
        fun `writes a negative integer in two's complement`() {
            assertThat(ConstantIntegerInfo(-1).serialized())
                .containsExactly(*bytesOf(0x03, 0xFF, 0xFF, 0xFF, 0xFF))
        }

        @Test
        fun `writes a float as its ieee 754 bit pattern`() {
            assertThat(ConstantFloatInfo(1.5f).serialized())
                .containsExactly(*bytesOf(0x04, 0x3F, 0xC0, 0x00, 0x00))
        }

        @Test
        fun `writes a long as eight big-endian bytes`() {
            assertThat(ConstantLongInfo(0x0102030405060708L).serialized())
                .containsExactly(*bytesOf(0x05, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08))
        }

        @Test
        fun `writes a negative long in two's complement`() {
            assertThat(ConstantLongInfo(-1L).serialized())
                .containsExactly(*bytesOf(0x05, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF))
        }

        @Test
        fun `writes a double as its ieee 754 bit pattern`() {
            assertThat(ConstantDoubleInfo(1.5).serialized())
                .containsExactly(*bytesOf(0x06, 0x3F, 0xF8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        }

        @Test
        fun `keeps negative zero distinct from positive zero`() {
            assertThat(ConstantDoubleInfo(-0.0).serialized())
                .containsExactly(*bytesOf(0x06, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
            assertThat(ConstantDoubleInfo(0.0).serialized())
                .containsExactly(*bytesOf(0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        }
    }

    @Nested
    inner class References {

        @Test
        fun `writes a class as tag and name index`() {
            assertThat(ConstantClassInfo(nameIndex = 1).serialized())
                .containsExactly(*bytesOf(0x07, 0x00, 0x01))
        }

        @Test
        fun `writes a string as tag and value index`() {
            assertThat(ConstantStringInfo(valueIndex = 2).serialized())
                .containsExactly(*bytesOf(0x08, 0x00, 0x02))
        }

        @Test
        fun `writes a field ref as tag, class index and name-and-type index`() {
            assertThat(ConstantFieldRefInfo(classNameIndex = 3, nameAndTypeIndex = 4).serialized())
                .containsExactly(*bytesOf(0x09, 0x00, 0x03, 0x00, 0x04))
        }

        @Test
        fun `writes a method ref as tag, class index and name-and-type index`() {
            assertThat(ConstantMethodRefInfo(classNameIndex = 3, nameAndTypeIndex = 4).serialized())
                .containsExactly(*bytesOf(0x0A, 0x00, 0x03, 0x00, 0x04))
        }

        @Test
        fun `writes an interface method ref as tag, class index and name-and-type index`() {
            assertThat(ConstantInterfaceMethodRefInfo(classNameIndex = 3, nameAndTypeIndex = 4).serialized())
                .containsExactly(*bytesOf(0x0B, 0x00, 0x03, 0x00, 0x04))
        }

        @Test
        fun `writes a name-and-type as tag, name index and descriptor index`() {
            assertThat(ConstantNameAndTypeInfo(nameIndex = 5, descriptorIndex = 6).serialized())
                .containsExactly(*bytesOf(0x0C, 0x00, 0x05, 0x00, 0x06))
        }

        @Test
        fun `writes an index above 255 as two big-endian bytes`() {
            assertThat(ConstantClassInfo(nameIndex = 258).serialized())
                .containsExactly(*bytesOf(0x07, 0x01, 0x02))
        }
    }

    @Nested
    inner class Pool {

        @Test
        fun `writes an empty pool as just the count`() {
            assertThat(StaticConstantPool(1, emptyList()).serialized())
                .containsExactly(*bytesOf(0x00, 0x01))
        }

        @Test
        fun `writes the count followed by the entries in order`() {
            // given
            val pool = StaticConstantPool(
                3,
                listOf(ConstantUtf8Info("A"), ConstantIntegerInfo(1)),
            )

            // then
            assertThat(pool.serialized()).containsExactly(
                *bytesOf(
                    0x00, 0x03,                    // constant_pool_count
                    0x01, 0x00, 0x01, 0x41,        // utf8 "A"
                    0x03, 0x00, 0x00, 0x00, 0x01,  // integer 1
                ),
            )
        }

        @Test
        fun `writes a count above 255 as two big-endian bytes`() {
            assertThat(StaticConstantPool(258, emptyList()).serialized())
                .containsExactly(*bytesOf(0x01, 0x02))
        }

        @Test
        fun `writes a pool built from the updatable pool`() {
            // given
            val pool = UpdatableConstantPool().apply { putClass("A") }.build()

            // then
            assertThat(pool.serialized()).containsExactly(
                *bytesOf(
                    0x00, 0x03,              // count: two entries occupy indices 1 and 2
                    0x01, 0x00, 0x01, 0x41,  // utf8 "A" at index 1
                    0x07, 0x00, 0x01,        // class pointing at index 1
                ),
            )
        }

        @Test
        fun `counts the unused second slot of an eight-byte constant`() {
            // given
            val pool = UpdatableConstantPool().apply { putLong(1L) }.build()

            // then
            assertThat(pool.serialized()).containsExactly(
                *bytesOf(
                    0x00, 0x03,  // count is 3 even though only one entry is written
                    0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
                ),
            )
        }

        @Test
        fun `writes a method ref pool that resolves through its indices`() {
            // given
            val pool = UpdatableConstantPool().apply {
                val clazz = putClass("A")
                putRef(clazz, "f", "()V", UpdatableConstantPool.RefType.METHOD)
            }.build()

            // then
            assertThat(pool.serialized()).containsExactly(
                *bytesOf(
                    0x00, 0x07,                          // count: six entries
                    0x01, 0x00, 0x01, 0x41,              // 1: utf8 "A"
                    0x07, 0x00, 0x01,                    // 2: class -> 1
                    0x01, 0x00, 0x01, 0x66,              // 3: utf8 "f"
                    0x01, 0x00, 0x03, 0x28, 0x29, 0x56,  // 4: utf8 "()V"
                    0x0C, 0x00, 0x03, 0x00, 0x04,        // 5: name-and-type -> 3, 4
                    0x0A, 0x00, 0x02, 0x00, 0x05,        // 6: method ref -> 2, 5
                ),
            )
        }
    }
}
