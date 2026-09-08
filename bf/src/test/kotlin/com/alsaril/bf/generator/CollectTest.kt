package com.alsaril.bf.generator

import com.alsaril.codegen.classfile.Fragment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * `collect` takes as many fragments as fit one chunk and says where the next chunk starts,
 * which is what turns a program into a sequence of methods. Only sizes matter here, so the
 * fragments carry nothing else.
 */
class CollectTest {

    private fun of(vararg sizes: Int) =
        sizes.map { Fragment(listOf(ByteArray(it)), emptyList(), emptyList(), maxStack = 0, size = it) }

    private fun Chunk.sizes() = fragments.map { it.size }

    @Test
    fun `takes every fragment when the whole list fits`() {
        // given
        val chunk = collect(of(1, 2, 3), start = 0, maxsize = 10)

        // then nothing is left over
        assertThat(chunk.sizes()).containsExactly(1, 2, 3)
        assertThat(chunk.next).isNull()
    }

    @Test
    fun `stops before the fragment that would overflow`() {
        // given
        val chunk = collect(of(4, 4, 4), start = 0, maxsize = 9)

        // then the chunk ends and the one that did not fit starts the next
        assertThat(chunk.sizes()).containsExactly(4, 4)
        assertThat(chunk.next).isEqualTo(2)
    }

    @Test
    fun `starts from the index it is given`() {
        // given the same list picked up where the previous chunk left off
        val chunk = collect(of(4, 4, 4), start = 1, maxsize = 9)

        // then
        assertThat(chunk.sizes()).containsExactly(4, 4)
        assertThat(chunk.next).isNull()
    }

    @Test
    fun `fills a chunk exactly to the limit`() {
        assertThat(collect(of(5, 5), start = 0, maxsize = 10).sizes()).containsExactly(5, 5)
    }

    @Test
    fun `treats one byte past the limit as the next chunk`() {
        // given
        val chunk = collect(of(5, 6), start = 0, maxsize = 10)

        // then
        assertThat(chunk.sizes()).containsExactly(5)
        assertThat(chunk.next).isEqualTo(1)
    }

    @Test
    fun `collects nothing from an empty list`() {
        // given
        val chunk = collect(emptyList(), start = 0, maxsize = 10)

        // then
        assertThat(chunk.fragments).isEmpty()
        assertThat(chunk.next).isNull()
    }

    @Test
    fun `collects nothing when the start is past the end`() {
        // given
        val chunk = collect(of(1), start = 1, maxsize = 10)

        // then
        assertThat(chunk.fragments).isEmpty()
        assertThat(chunk.next).isNull()
    }

    /**
     * A fragment larger than a whole chunk cannot be split any further, so refusing it
     * would hand the caller a chunk it cannot use and an index that has not moved. Which
     * of the two is wanted depends on the caller, hence the flag.
     */
    @Nested
    inner class AFragmentLargerThanAChunk {

        @Test
        fun `is refused by default, leaving the start where it was`() {
            // given
            val chunk = collect(of(11, 1), start = 0, maxsize = 10)

            // then an empty chunk pointing at itself is what tells tryInline to give up
            assertThat(chunk.fragments).isEmpty()
            assertThat(chunk.next).isZero()
        }

        @Test
        fun `is taken when spilling is allowed, so the packer makes progress`() {
            // given
            val chunk = collect(of(11, 1), start = 0, maxsize = 10, allowSingleFragmentSpill = true)

            // then the chunk is over the limit by exactly the one fragment that had to go
            // somewhere, and the start has moved on
            assertThat(chunk.sizes()).containsExactly(11)
            assertThat(chunk.next).isEqualTo(1)
        }

        @Test
        fun `is only spilled into a chunk of its own`() {
            // given a chunk that has already taken something
            val chunk = collect(of(1, 11), start = 0, maxsize = 10, allowSingleFragmentSpill = true)

            // then the oversized fragment still ends the chunk, and spills into the next
            assertThat(chunk.sizes()).containsExactly(1)
            assertThat(chunk.next).isEqualTo(1)

            val spilled = collect(of(1, 11), start = 1, maxsize = 10, allowSingleFragmentSpill = true)

            assertThat(spilled.sizes()).containsExactly(11)
            assertThat(spilled.next).isNull()
        }
    }
}
