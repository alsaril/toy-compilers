package com.alsaril.bf

import com.alsaril.bf.Compiler.compile
import org.assertj.core.api.Assertions.*
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
        fun `compiles a pointer move longer than one instruction can hold`() {
            // 70000 is past Short.MAX_VALUE, so each move is emitted as several iincs
            val n = 70_000

            // mark a far cell, walk all the way back, then read both ends
            val source = ">".repeat(n) + "+".repeat(65) + "." + "<".repeat(n) + "."

            assertThat(run(source, memsize = 100_000)).containsExactly(65, 0)
        }

        @Test
        fun `keeps the bounds check across a split pointer move`() {
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { run("<".repeat(70_000) + ".") }
                .withMessage("Buffer overflow")
        }

        @Test
        fun `wraps a run of exactly 256 increments back to zero`() {
            assertThat(run("+".repeat(256) + ".")).containsExactly(0)
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

    /**
     * A folded run is a single fragment, so a long program needs many *distinct*
     * instructions to grow the method. "+-" alternates, defeating the folding.
     */
    @Nested
    inner class Splitting {

        @Test
        fun `runs a program small enough to stay in one method`() {
            assertThat(run("+".repeat(65) + ".").text()).isEqualTo("A")
        }

        @Test
        fun `runs a program past the method length threshold`() {
            // ~700 instructions is beyond the 8000 byte budget for a single method
            val padding = "+-".repeat(350)

            assertThat(run(padding + "+".repeat(65) + ".").text()).isEqualTo("A")
        }

        @Test
        fun `runs a program past the hard method length limit`() {
            // a single method cannot exceed 65535 bytes of bytecode, so without
            // splitting the class would be rejected at load time
            val padding = "+-".repeat(2500)

            assertThat(run(padding + "+".repeat(65) + ".").text()).isEqualTo("A")
        }

        @Test
        fun `keeps a loop working when its body is split`() {
            // the body is long enough to be outlined, and runs three times
            val body = "+-".repeat(400) + ">+<"

            assertThat(run("+++[-" + body + "]>.").text()).isEqualTo("\u0003")
        }

        @Test
        fun `keeps a loop working when the program around it is split`() {
            val padding = "+-".repeat(400)

            assertThat(run(padding + "+++[->+<]" + padding + ">.")).containsExactly(3)
        }

        @Test
        fun `keeps input and output working across a split`() {
            val padding = "+-".repeat(400)

            assertThat(run(padding + ",." + padding + ",.", input = "hi").text()).isEqualTo("hi")
        }

        @Test
        fun `keeps the cycle limit working across a split`() {
            val padding = "+-".repeat(400)

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { run(padding + "+[]", cycles = 100) }
                .withMessage("Cycles overflow")
        }

        @Test
        fun `keeps the bounds check working across a split`() {
            val padding = "+-".repeat(400)

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { run(padding + "<.") }
                .withMessage("Buffer overflow")
        }
    }

    @Nested
    inner class Parsing {

        @Test
        fun `rejects an unclosed bracket`() {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { compile("+[+") }
                .withMessage("']' expected at 3")
        }

        @Test
        fun `rejects an unclosed nested bracket`() {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { compile("[[]") }
                .withMessageContaining("']' expected")
        }

        @Test
        fun `rejects a stray closing bracket`() {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { compile("]") }
                .withMessage("unexpected ']' at 0")
        }

        @Test
        fun `reports where the error is`() {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { compile("++]") }
                .withMessage("unexpected ']' at 2")
        }

        @Test
        fun `counts a position past a comment`() {
            // skipped characters still count towards the index
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { compile("+ hey ]") }
                .withMessage("unexpected ']' at 6")
        }

        @Test
        fun `rejects a bracket opened at the very start`() {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { compile("[") }
        }

        @Test
        fun `accepts balanced brackets`() {
            assertThatNoException().isThrownBy { compile("[[][]]") }
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
