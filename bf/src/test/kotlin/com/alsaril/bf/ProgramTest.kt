package com.alsaril.bf

import com.alsaril.bf.Compiler.compile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ProgramTest {

    private fun run(
        source: String,
        input: String = "",
        memsize: Int = 30_000,
        cycles: Int = 1_000_000,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        compile(source).run(
            ByteArrayInputStream(input.toByteArray(Charsets.ISO_8859_1)),
            output,
            memsize,
            cycles,
        )
        return output.toByteArray()
    }

    private fun ByteArray.text() = String(this, Charsets.ISO_8859_1)

    @Nested
    inner class Programs {

        @Test
        fun `emits nothing for an empty program`() {
            assertThat(run("")).isEmpty()
        }

        @Test
        fun `ignores characters that are not commands`() {
            assertThat(run("++ hey you ++.")).isEqualTo(run("++++."))
        }

        @Test
        fun `prints hello world`() {
            val source = "++++++++[>++++[>++>+++>+++>+<<<<-]>+>+>->>+[<]<-]" +
                ">>.>---.+++++++..+++.>>.<-.<.+++.------.--------.>>+.>++."

            assertThat(run(source).text()).isEqualTo("Hello World!\n")
        }

        @Test
        fun `echoes its input`() {
            assertThat(run(",.,.,.", input = "abc").text()).isEqualTo("abc")
        }

        @Test
        fun `runs the usual cat loop to completion`() {
            // ,[.,] terminates only because reading past the end leaves a zero cell
            assertThat(run(",[.,]", input = "abc").text()).isEqualTo("abc")
        }

        @Test
        fun `compiles a run longer than one instruction can hold`() {
            // 100000 increments wrap to 160 within the byte cell
            assertThat(run("+".repeat(100_000) + ".")).containsExactly(160.toByte())
        }

        @Test
        fun `multiplies with a loop`() {
            // 7 * 7 = 49, the code point of '1'
            assertThat(run("+++++++[>+++++++<-]>.").text()).isEqualTo("1")
        }

        @Test
        fun `moves a value between cells`() {
            assertThat(run("+++++++++++++++++++++++++++++++++[>+<-]>.").text())
                .isEqualTo("!")
        }
    }

    @Nested
    inner class CellSemantics {

        @Test
        fun `wraps a cell below zero`() {
            assertThat(run("-.")).containsExactly(0xFF.toByte())
        }

        @Test
        fun `wraps a cell above the byte range`() {
            assertThat(run("-".repeat(1) + "+".repeat(1) + "+.")).containsExactly(1)
        }

        @Test
        fun `reads zero at the end of input`() {
            // InputStream.read returns -1, which is clamped to an empty cell
            assertThat(run(",.", input = "")).containsExactly(0)
        }

        @Test
        fun `keeps a real 0xFF byte distinct from end of input`() {
            assertThat(run(",.", input = "\u00FF")).containsExactly(0xFF.toByte())
        }

        @Test
        fun `starts every cell at zero`() {
            assertThat(run(".")).containsExactly(0)
        }
    }

    @Nested
    inner class Bounds {

        @Test
        fun `rejects moving the pointer before the tape`() {
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { run("<.") }
                .withMessage("Buffer overflow")
        }

        @Test
        fun `rejects moving the pointer past the tape`() {
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { run(">.", memsize = 1) }
                .withMessage("Buffer overflow")
        }

        @Test
        fun `uses the whole tape it is given`() {
            assertThatNoException().isThrownBy { run(">.", memsize = 2) }
        }
    }

    @Nested
    inner class CycleLimit {

        // the loop body runs exactly three times, once per decrement
        private val threeIterations = "+++[-]"

        @Test
        fun `allows exactly the requested number of iterations`() {
            assertThatNoException().isThrownBy { run(threeIterations, cycles = 3) }
        }

        @Test
        fun `stops one iteration short of the limit`() {
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { run(threeIterations, cycles = 2) }
                .withMessage("Cycles overflow")
        }

        @Test
        fun `does not spend a cycle on a loop it never enters`() {
            // the condition is false immediately, so no iteration is charged
            assertThatNoException().isThrownBy { run("[-]", cycles = 0) }
        }

        @Test
        fun `bounds a program that would otherwise never end`() {
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { run("+[]", cycles = 1_000) }
                .withMessage("Cycles overflow")
        }
    }

    @Nested
    inner class Compilation {

        @Test
        fun `produces something runnable as a plain Runnable too`() {
            assertThat(compile("")).isInstanceOf(Runnable::class.java)
        }

        @Test
        fun `compiles each program to its own class`() {
            assertThat(compile("+")).isNotSameAs(compile("+"))
        }
    }
}
