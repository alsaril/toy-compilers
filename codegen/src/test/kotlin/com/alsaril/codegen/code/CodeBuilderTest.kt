package com.alsaril.codegen.code

import com.alsaril.codegen.bytesOf
import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.alsaril.codegen.instruction.*

class CodeBuilderTest {

    @Test
    fun `starts empty`() {
        // given
        val fragment = builder().build()

        // then
        assertThat(fragment.size).isZero()
    }

    @Test
    fun `reports the current offset as code is emitted`() {
        // given
        val builder = builder()

        // then
        assertThat(builder.build().size).isZero()
        with(builder) { +nop }
        assertThat(builder.build().size).isEqualTo(1)
        with(builder) { +goto }
        assertThat(builder.build().size).isEqualTo(4)
    }

    @Nested
    inner class Labels {

        @Test
        fun `refuses a jump target from another builder`() {
            // given a label naming instruction 0 of a builder that is not this one
            val elsewhere = builder().apply { +nop }
            val target = with(elsewhere) { +nop }

            // then it is refused rather than reaching instruction 0 of this one
            assertThatIllegalArgumentException()
                .isThrownBy { builder().apply { link(+goto, target) } }
                .withMessageContaining("handed out by another builder")
        }

        @Test
        fun `refuses a guarded range from another builder`() {
            val other = builder()
            val from = with(other) { +nop }
            val to = with(other) { +nop }

            assertThatIllegalArgumentException()
                .isThrownBy { builder().apply { +nop; +nop; `catch`(from, to, to, type = null) } }
                .withMessageContaining("handed out by another builder")
        }

        @Test
        fun `takes the label a splice hands back, which this builder owns`() {
            val piece = builder().apply { +aconst_null }.build()

            val code = bytecode {
                val jump = +goto
                link(jump, fragment(piece)!!)
            }

            assertThat(code).containsExactly(*bytesOf(0xA7, 0x00, 0x03, 0x01))
        }
    }

    @Nested
    inner class Transforming {

        @Test
        fun `replaces an instruction without moving the ones around it`() {
            val code = bytecode {
                +nop
                +fload(1)
                +nop
                transform { i, instruction -> if (i == 1) fload(2) else null }
            }

            assertThat(code).containsExactly(*bytesOf(0x00, 0x24, 0x00))
        }

        @Test
        fun `keeps the size in step when a replacement encodes wider`() {
            // given a compact load, which is one byte
            val builder = builder().apply { +fload(1) }
            assertThat(builder.size()).isOne()

            // when it is re-slotted past the compact range, taking the wide form
            builder.transform { _, _ -> fload(300) }

            // then the size follows the bytes rather than the instruction it replaced
            assertThat(builder.size()).isEqualTo(4)
            assertThat(builder.build().size).isEqualTo(4)
            assertThat(builder.build().bytecode()).hasSize(4)
        }

        @Test
        fun `keeps the size in step when a replacement encodes narrower`() {
            // given
            val builder = builder().apply { +fload(300) }
            assertThat(builder.size()).isEqualTo(4)

            // when
            builder.transform { _, _ -> fload(1) }

            // then
            assertThat(builder.size()).isOne()
            assertThat(builder.build().size).isOne()
            assertThat(builder.build().bytecode()).hasSize(1)
        }

        @Test
        fun `leaves the size alone when nothing is replaced`() {
            val builder = builder().apply { +fload(300); +nop }

            builder.transform { _, _ -> null }

            assertThat(builder.size()).isEqualTo(5)
        }
    }

    @Nested
    inner class Splicing {

        private fun piece(block: CodeBuilder.() -> Unit) = builder().apply(block).build()

        @Test
        fun `appends the bytes where the builder had got to`() {
            assertThat(bytecode { +nop; fragment(piece { +aconst_null; +iconst(-1) }) })
                .containsExactly(*bytesOf(0x00, 0x01, 0x02))
        }

        @Test
        fun `appends several fragments in the order they were spliced`() {
            assertThat(bytecode { fragment(piece { +aconst_null }); fragment(piece { +iconst(-1); +iconst(0) }) })
                .containsExactly(*bytesOf(0x01, 0x02, 0x03))
        }

        @Test
        fun `takes a fragment with nothing in it`() {
            assertThat(bytecode { +nop; fragment(piece { }); +nop })
                .containsExactly(*bytesOf(0x00, 0x00))
        }

        @Test
        fun `keeps emitting after a fragment`() {
            assertThat(bytecode { fragment(piece { +aconst_null }); +nop })
                .containsExactly(*bytesOf(0x01, 0x00))
        }

        @Test
        fun `slides an exception handler by where the fragment landed`() {
            // given a fragment guarding its own first instruction
            val piece = builder().apply {
                val from = +nop
                val caught = +nop
                `catch`(from, caught, caught, type = null)
            }.build()

            // then the row moves with the code it guards
            assertThat(builder().apply { repeat(4) { +nop }; fragment(piece) }.build().exceptionHandlers)
                .containsExactly(ExceptionHandler(4, 5, 5, catchType = null))
        }

        @Test
        fun `slides every handler of the fragment`() {
            // given
            val piece = builder().apply {
                val a = +nop; val b = +nop; val c = +nop; val d = +nop
                `catch`(a, b, b, type = null)
                `catch`(c, d, d, type = null)
            }.build()

            // then
            assertThat(builder().apply { +nop; fragment(piece) }.build().exceptionHandlers)
                .containsExactly(
                    ExceptionHandler(1, 2, 2, catchType = null),
                    ExceptionHandler(3, 4, 4, catchType = null),
                )
        }

        @Test
        fun `hands back the label of where the fragment landed`() {
            // given a jump that has to reach code arriving from somewhere else
            val code = bytecode {
                val jump = +goto
                val landed = fragment(piece { +aconst_null; +iconst(-1) })!!
                link(jump, landed)
            }

            // then the label names the fragment's first instruction, past the jump
            assertThat(code).containsExactly(*bytesOf(0xA7, 0x00, 0x03, 0x01, 0x02))
        }

        @Test
        fun `hands back nothing for a fragment with no instructions`() {
            // there is no position for an empty splice to have landed at, and saying so
            // beats naming whatever is emitted next
            val builder = builder()

            assertThat(builder.fragment(piece { })).isNull()
            assertThat(builder.fragment(piece { +nop })).isNotNull()
        }

        @Test
        fun `shifts the links of a fragment by instructions rather than bytes`() {
            // given a fragment reaching its own target
            val open = builder().apply {
                val jump = +goto
                link(jump, +nop)
            }.build()

            // when it is spliced behind a single instruction three bytes wide
            val code = bytecode { +goto; fragment(open) }

            // then the link moved by the one instruction ahead of it, not by its three
            // bytes: a jump and its target are named by index, and only the layout is
            // measured in bytes
            assertThat(code).containsExactly(*bytesOf(0xA7, 0x00, 0x00, 0xA7, 0x00, 0x03, 0x00))
        }

        @Test
        fun `takes a fragment whose jump was linked before it was spliced`() {
            // given a fragment reaching its own target
            val open = builder().apply {
                val jump = +goto
                link(jump, +nop)
            }.build()

            // then the offset still reaches it from wherever the fragment landed
            assertThat(bytecode { +nop; fragment(open) })
                .containsExactly(*bytesOf(0x00, 0xA7, 0x00, 0x03, 0x00))
        }
    }

    @Nested
    inner class Building {

        @Test
        fun `builds the same fragment more than once`() {
            // given
            val builder = builder().apply { +nop }

            // then
            assertThat(builder.build()).isEqualTo(builder.build())
        }

        @Test
        fun `keeps emitting after a build`() {
            // given
            val builder = builder().apply { +nop }
            builder.build()

            // when
            with(builder) { +nop }

            // then
            assertThat(builder.build().size).isEqualTo(2)
        }

        @Test
        fun `links a jump raised before building`() {
            // given
            val builder = builder()
            val from = with(builder) { +goto }
            with(builder) { +nop }
            builder.link(from, with(builder) { +nop })

            // then
            assertThat(builder.build().bytecode())
                .containsExactly(*bytesOf(0xA7, 0x00, 0x04, 0x00, 0x00))
        }

        @Test
        fun `still reports the size after building`() {
            // given
            val builder = builder().apply { +nop; +nop }
            builder.build()

            // then
            assertThat(builder.build().size).isEqualTo(2)
        }
    }
}
