package com.alsaril.bf

import com.alsaril.bf.BfCompiler.compile
import com.alsaril.bf.generator.ClassGenerator.generate
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream

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

    /** hands over what was written only once the stream has been flushed */
    private class LateStream : OutputStream() {
        private val pending = ByteArrayOutputStream()
        private val flushed = ByteArrayOutputStream()

        var flushes = 0
            private set

        override fun write(b: Int) = pending.write(b)

        override fun flush() {
            flushes++
            flushed.write(pending.toByteArray())
            pending.reset()
        }

        fun text() = String(flushed.toByteArray(), Charsets.ISO_8859_1)
    }

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
            // 70000 is past Short.MAX_VALUE, so the move is added in several iconst steps
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

        @Test
        fun `allows the pointer to leave the tape and come back`() {
            assertThat(run("><.", memsize = 1)).containsExactly(0)
            assertThat(run(">>>>><<<<<.", memsize = 1)).containsExactly(0)
            assertThat(run("<>.")).containsExactly(0)
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
        fun `compiles a loop that lands right on the chunk budget`() {
            // a loop body of 265 pairs plus one move used to make a fragment of 7997,
            // one byte past the chunk budget, leaving collect with nothing to take
            val source = "[" + "+-".repeat(265) + ">" + "]" + "+-".repeat(10)

            assertThat(run(source)).isEmpty()
        }

        @Test
        fun `compiles a deeply nested program`() {
            // generating recursively used to overflow the stack somewhere above 3000
            val depth = 10_000

            // the outer loop sees a zero cell, so the whole nest is skipped at runtime
            assertThat(run("[".repeat(depth) + "+" + "]".repeat(depth))).isEmpty()
        }

        @Test
        fun `compiles a program with tens of thousands of instructions`() {
            val padding = "+-".repeat(20_000)

            assertThat(run(padding + "+".repeat(65) + ".").text()).isEqualTo("A")
        }

        @Test
        fun `compiles a loop whose body is far larger than one method`() {
            // the body outlines into its own methods, called once per iteration
            val body = "-" + "+-".repeat(10_000) + ">+<"

            assertThat(run("+++[" + body + "]>.")).containsExactly(3)
        }

        @Test
        fun `compiles a program with thousands of loops`() {
            assertThat(run("[+-]".repeat(2_000) + "+".repeat(65) + ".").text()).isEqualTo("A")
        }

        @Test
        fun `splits a large program into a proportional number of methods`() {
            // the chunker once added the fragment count rather than the fragment size,
            // which grew the method count with the square of the program
            val (_, bytes) = generate(Parser.parse("+-".repeat(2_500)))

            assertThat(methodCodeLengths(bytes)).hasSizeLessThan(50)
        }

        @Test
        fun `keeps every method inside the method length limit`() {
            // 8000 is hotspot's threshold for compiling a method at all, so a method
            // past it would silently stay interpreted
            val sources = listOf(
                "+".repeat(65) + ".",
                "+-".repeat(350),
                "+-".repeat(2500),
                "[" + "+-".repeat(265) + ">" + "]",
                "[" + "+-".repeat(265) + ">" + "]" + "+-".repeat(10),
                "+++[->" + "+-".repeat(400) + "<]",
            )

            sources.forEach { source ->
                val (_, bytes) = generate(Parser.parse(source))

                assertThat(methodCodeLengths(bytes))
                    .allSatisfy { assertThat(it).isLessThanOrEqualTo(8000) }
            }
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
        fun `generates a class that implements Program and nothing else`() {
            assertThat(compile("").javaClass.interfaces).containsExactly(Program::class.java)
        }

        @Test
        fun `compiles each program to its own class`() {
            assertThat(compile("+")).isNotSameAs(compile("+"))
        }
    }

    /**
     * A program is compiled to a try/finally around its body, so the stream it was handed
     * is flushed whether the body returns or is stopped part way through. Nothing else
     * flushes it, and a ByteArrayOutputStream cannot tell the difference, so these tests
     * run against a LateStream instead.
     */
    @Nested
    inner class Flushing {

        private fun runInto(
            out: OutputStream,
            source: String,
            input: String = "",
            memsize: Int = 30_000,
            cycles: Int = 1_000_000,
        ) = compile(source).run(
            ByteArrayInputStream(input.toByteArray(Charsets.ISO_8859_1)),
            out,
            memsize,
            cycles,
        )

        private val printsA = "+".repeat(65) + "."

        @Test
        fun `flushes once when the program returns`() {
            // given
            val out = LateStream()

            // when
            runInto(out, printsA)

            // then
            assertThat(out.text()).isEqualTo("A")
            assertThat(out.flushes).isOne()
        }

        @Test
        fun `flushes even when the program writes nothing`() {
            // given
            val out = LateStream()

            // when
            runInto(out, "+")

            // then
            assertThat(out.flushes).isOne()
        }

        @Test
        fun `flushes what was written before a bounds failure`() {
            // given
            val out = LateStream()

            // when the pointer walks off the tape after printing
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { runInto(out, printsA + "<.") }
                .withMessage("Buffer overflow")

            // then the output survives the failure, which is still raised
            assertThat(out.text()).isEqualTo("A")
            assertThat(out.flushes).isOne()
        }

        @Test
        fun `flushes what was written before the cycle limit stops the program`() {
            // given
            val out = LateStream()

            // when
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { runInto(out, printsA + "+[]", cycles = 100) }
                .withMessage("Cycles overflow")

            // then
            assertThat(out.text()).isEqualTo("A")
            assertThat(out.flushes).isOne()
        }

        @Test
        fun `flushes what was written from an outlined method`() {
            // given a program large enough that the writes live in their own methods
            val out = LateStream()
            val padding = "+-".repeat(400)

            // when
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { runInto(out, padding + printsA + padding + "<.") }
                .withMessage("Buffer overflow")

            // then the handler on the outermost frame still catches it
            assertThat(out.text()).isEqualTo("A")
            assertThat(out.flushes).isOne()
        }

        @Test
        fun `lets a failure from the stream itself through`() {
            // given a stream that fails on the write the program asks for
            val out = object : OutputStream() {
                override fun write(b: Int) = throw UnsupportedOperationException("nope")
            }

            // then the finally does not turn it into something else
            assertThatExceptionOfType(UnsupportedOperationException::class.java)
                .isThrownBy { runInto(out, printsA) }
                .withMessage("nope")
        }
    }
}
