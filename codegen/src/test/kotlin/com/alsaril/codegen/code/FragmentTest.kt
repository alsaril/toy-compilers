package com.alsaril.codegen.code

import com.alsaril.codegen.constantpool.ClassPointer
import com.alsaril.codegen.bytesOf
import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.alsaril.codegen.instruction.*


class FragmentTest {

    private fun fragment(size: Int) = builder().apply {
        repeat(size) { +nop }
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
            assertThat(Fragment(emptyList(), emptyMap(), emptyList(), size = 0).bytecode())
                .isEmpty()
        }
    }

    @Nested
    inner class Join {

        @Test
        fun `hands back a lone fragment untouched`() {
            // given
            val only = fragment(3)

            // then
            assertThat(listOf(only).join()).isSameAs(only)
        }

        @Test
        fun `produces an empty fragment for no input`() {
            // given
            val joined = emptyList<Fragment>().join()

            // then
            assertThat(joined.size).isZero()
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

    }

    @Nested
    inner class JoinedHandlers {

        // `size` nops guarded by rows already written against this fragment's own indices
        private fun guarded(size: Int, vararg handlers: ExceptionHandler) =
            Fragment(List(size) { nop }, emptyMap(), handlers.toList(), size)

        @Test
        fun `hands back a lone fragment's handlers untouched`() {
            // given
            val handler = ExceptionHandler(1, 2, 2, catchType = ClassPointer(3, "E"))
            val only = guarded(3, handler)

            // then
            assertThat(listOf(only).join().exceptionHandlers).containsExactly(handler)
        }

        @Test
        fun `leaves the handlers of the first fragment where they are`() {
            // given
            val joined = listOf(
                guarded(4, ExceptionHandler(0, 2, 3, catchType = null)),
                guarded(1),
            ).join()

            // then
            assertThat(joined.exceptionHandlers)
                .containsExactly(ExceptionHandler(0, 2, 3, catchType = null))
        }

        @Test
        fun `shifts every location by how far its fragment moved`() {
            // given a handler covering the whole of a fragment that lands at index 5
            val joined = listOf(
                guarded(5),
                guarded(4, ExceptionHandler(0, 2, 3, catchType = ClassPointer(7, "E"))),
            ).join()

            // then the range and the handler move together, and the caught type does not
            assertThat(joined.exceptionHandlers)
                .containsExactly(ExceptionHandler(5, 7, 8, catchType = ClassPointer(7, "E")))
        }

        @Test
        fun `collects the handlers of every fragment in order`() {
            // given
            val joined = listOf(
                guarded(2, ExceptionHandler(0, 1, 1, catchType = null)),
                guarded(2, ExceptionHandler(0, 1, 1, catchType = null)),
                guarded(2, ExceptionHandler(0, 1, 1, catchType = null)),
            ).join()

            // then the order the jvm searches them in survives the join
            assertThat(joined.exceptionHandlers).containsExactly(
                ExceptionHandler(0, 1, 1, catchType = null),
                ExceptionHandler(2, 3, 3, catchType = null),
                ExceptionHandler(4, 5, 5, catchType = null),
            )
        }

        @Test
        fun `keeps several handlers of one fragment together`() {
            // given
            val joined = listOf(
                guarded(1),
                guarded(
                    4,
                    ExceptionHandler(0, 1, 2, catchType = null),
                    ExceptionHandler(1, 2, 3, catchType = null),
                ),
            ).join()

            // then
            assertThat(joined.exceptionHandlers).containsExactly(
                ExceptionHandler(1, 2, 3, catchType = null),
                ExceptionHandler(2, 3, 4, catchType = null),
            )
        }

        @Test
        fun `moves handlers by the fragments before them`() {
            // given a fragment carrying a handler
            val body = Fragment(
                List(3) { nop },
                emptyMap(),
                listOf(ExceptionHandler(0, 1, 1, catchType = null)),
                size = 3,
            )
            val joined = listOf(fragment(2), body).join()

            // then the handler counts from zero
            assertThat(joined.exceptionHandlers)
                .containsExactly(ExceptionHandler(2, 3, 3, catchType = null))
        }
    }
}
