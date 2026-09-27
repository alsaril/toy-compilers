package com.alsaril.codegen.code

import com.alsaril.codegen.verification.PrimitiveType.*
import com.alsaril.codegen.verification.ReferenceType
import com.alsaril.codegen.verification.Uninitialized
import com.alsaril.codegen.instruction.iload
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The local slots the analyzer tracks, after JVMS 4.10.1.2: a long or a double is its type in
 * one slot and top in the next, and every change keeps that pair together.
 */
class LocalsTest {

    private val string = ReferenceType("java/lang/String")

    @Test
    fun `lay the arguments out from slot 0, a long or a double over two slots`() {
        assertThat(Locals.of(listOf(INTEGER, LONG, string, DOUBLE)))
            .isEqualTo(Locals(listOf(INTEGER, LONG, TOP, string, DOUBLE, TOP)))
        assertThat(Locals.of(emptyList())).isEqualTo(Locals(emptyList()))
    }

    @Test
    fun `refuse a long or a double without top after it`() {
        // anything that changes a slot has to keep the halves together, so this is a bug where it happens
        assertThatIllegalStateException()
            .isThrownBy { Locals(listOf(LONG, INTEGER)) }
            .withMessageContaining("LONG in local 0 is missing its second half")
        assertThatIllegalStateException()
            .isThrownBy { Locals(listOf(INTEGER, DOUBLE)) }
            .withMessageContaining("DOUBLE in local 1 is missing its second half")
    }

    @Nested
    inner class Reads {

        @Test
        fun `hand back what the slot holds`() {
            val locals = Locals.of(listOf(INTEGER, LONG))

            assertThat(locals.read(0, iload(0), 3)).isEqualTo(INTEGER)
            assertThat(locals.read(1, iload(1), 3)).isEqualTo(LONG)
            assertThat(locals.read(2, iload(2), 3)).isEqualTo(TOP) // the second half, never usable on its own
        }

        @Test
        fun `refuse a slot past the ones defined`() {
            assertThatIllegalArgumentException()
                .isThrownBy { Locals.of(listOf(INTEGER)).read(1, iload(1), 3) }
                .withMessage("iload(index=1) at 3 reads local 1, but only 1 local slots are defined")
        }
    }

    @Nested
    inner class Writes {

        @Test
        fun `replace what a slot held`() {
            assertThat(Locals.of(listOf(INTEGER, FLOAT)).write(1, string))
                .isEqualTo(Locals(listOf(INTEGER, string)))
        }

        @Test
        fun `fill the slots they skip over with top`() {
            assertThat(Locals.of(listOf(INTEGER)).write(3, FLOAT))
                .isEqualTo(Locals(listOf(INTEGER, TOP, TOP, FLOAT)))
        }

        @Test
        fun `take two slots for a long`() {
            assertThat(Locals.of(listOf(INTEGER)).write(1, LONG)).isEqualTo(Locals(listOf(INTEGER, LONG, TOP)))
            assertThat(Locals.of(listOf(INTEGER, FLOAT, FLOAT)).write(1, LONG))
                .isEqualTo(Locals(listOf(INTEGER, LONG, TOP)))
        }

        @Test
        fun `cut a long written over at its first half`() {
            // its second half is left as a top that stands for nothing
            assertThat(Locals.of(listOf(LONG)).write(0, INTEGER)).isEqualTo(Locals(listOf(INTEGER, TOP)))
        }

        @Test
        fun `cut a long written over at its second half`() {
            assertThat(Locals.of(listOf(LONG)).write(1, INTEGER)).isEqualTo(Locals(listOf(TOP, INTEGER)))
            assertThat(Locals.of(listOf(LONG)).write(1, DOUBLE)).isEqualTo(Locals(listOf(TOP, DOUBLE, TOP)))
        }

        @Test
        fun `cut a long whose first half a wide write lands its own second half on`() {
            assertThat(Locals.of(listOf(INTEGER, LONG)).write(0, DOUBLE))
                .isEqualTo(Locals(listOf(DOUBLE, TOP, TOP)))
        }
    }

    @Nested
    inner class Merges {

        private fun merge(a: Locals, b: Locals) = a.merge(b) { x, y -> if (x == y) x else null }

        @Test
        fun `keep a slot both paths agree on`() {
            assertThat(merge(Locals.of(listOf(INTEGER, LONG)), Locals.of(listOf(INTEGER, LONG))))
                .isEqualTo(Locals.of(listOf(INTEGER, LONG)))
        }

        @Test
        fun `take a slot as the merge joins it`() {
            // given a merge that joins anything with a string as a string
            val merged = Locals.of(listOf(INTEGER, NULL)).merge(Locals.of(listOf(INTEGER, string))) { x, y ->
                if (x == y) x else string
            }

            // then
            assertThat(merged).isEqualTo(Locals.of(listOf(INTEGER, string)))
        }

        @Test
        fun `make a slot the paths cannot agree on top`() {
            assertThat(merge(Locals.of(listOf(INTEGER, FLOAT)), Locals.of(listOf(INTEGER, string))))
                .isEqualTo(Locals(listOf(INTEGER, TOP)))
        }

        @Test
        fun `lose a long that only one path holds, both of its halves`() {
            assertThat(merge(Locals.of(listOf(LONG)), Locals.of(listOf(INTEGER, INTEGER))))
                .isEqualTo(Locals(listOf(TOP, TOP)))
        }

        @Test
        fun `leave out a slot only one path defines`() {
            assertThat(merge(Locals.of(listOf(INTEGER, FLOAT)), Locals.of(listOf(INTEGER))))
                .isEqualTo(Locals.of(listOf(INTEGER)))
        }
    }

    @Test
    fun `initialise every slot holding an object when its constructor runs`() {
        // given two copies of the object made by the new at 4, and one made elsewhere
        val locals = Locals.of(listOf(Uninitialized(4), INTEGER, Uninitialized(4), Uninitialized(9)))

        // then
        assertThat(locals.initialise(Uninitialized(4), string))
            .isEqualTo(Locals.of(listOf(string, INTEGER, string, Uninitialized(9))))
    }

    @Test
    fun `list a long or a double once for both of its slots, as a frame does`() {
        assertThat(Locals.of(listOf(INTEGER, LONG, string, DOUBLE, TOP)).entries { it })
            .containsExactly(INTEGER, LONG, string, DOUBLE, TOP)
    }

    @Test
    fun `count every slot for max_locals, the second halves included`() {
        assertThat(Locals.of(listOf(INTEGER, LONG, DOUBLE)).size).isEqualTo(5)
    }
}
