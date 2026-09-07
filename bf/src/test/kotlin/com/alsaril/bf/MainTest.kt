package com.alsaril.bf

import com.github.ajalt.clikt.testing.test
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class MainTest {

    @TempDir
    lateinit var dir: File

    private class Invocation(val output: String, val error: String, val statusCode: Int)

    private fun bf(vararg args: String, source: String? = null, input: String = ""): Invocation {
        val argv = source
            ?.let { args.toList() + File(dir, "prog.bf").apply { writeText(it) }.path }
            ?: args.toList()

        val written = ByteArrayOutputStream()
        val out = System.out
        val stdin = System.`in`
        System.setOut(PrintStream(written, true))
        System.setIn(ByteArrayInputStream(input.toByteArray(Charsets.ISO_8859_1)))
        try {
            val result = BfCommand().test(argv)
            return Invocation(
                String(written.toByteArray(), Charsets.ISO_8859_1),
                result.stderr,
                result.statusCode,
            )
        } finally {
            System.setOut(out)
            System.setIn(stdin)
        }
    }

    private val helloWorld = "++++++++[>++++[>++>+++>+++>+<<<<-]>+>+>->>+[<]<-]" +
        ">>.>---.+++++++..+++.>>.<-.<.+++.------.--------.>>+.>++."

    @Nested
    inner class Runs {

        @Test
        fun `compiles the file it is given and writes what it prints`() {
            val result = bf(source = helloWorld)

            assertThat(result.output).isEqualTo("Hello World!\n")
            assertThat(result.statusCode).isZero()
        }

        @Test
        fun `feeds the program its standard input`() {
            assertThat(bf(source = ",[.,]", input = "abc").output).isEqualTo("abc")
        }
    }

    @Nested
    inner class Limits {

        @Test
        fun `defaults to a tape of thirty thousand cells`() {
            assertThat(bf(source = ">".repeat(29_999) + ".").statusCode).isZero()
            assertThat(bf(source = ">".repeat(30_000) + ".").error).contains("Buffer overflow")
        }

        @Test
        fun `defaults to a cycle limit high enough not to matter`() {
            assertThat(bf(source = "+".repeat(255) + "[-]").statusCode).isZero()
        }

        @Test
        fun `narrows the tape`() {
            val result = bf("--memsize=3", source = helloWorld)

            assertThat(result.error).contains("Buffer overflow")
            assertThat(result.statusCode).isOne()
        }

        @Test
        fun `widens the tape`() {
            assertThat(bf("--memsize=40000", source = ">".repeat(39_999) + ".").statusCode).isZero()
        }

        @Test
        fun `caps the loop iterations`() {
            val result = bf("--cycles=100", source = "+[]")

            assertThat(result.error).contains("Cycles overflow")
            assertThat(result.statusCode).isOne()
        }

        @Test
        fun `allows a cycle limit of zero`() {
            assertThat(bf("--cycles=0", source = "[-]").statusCode).isZero()
        }
    }

    @Nested
    inner class Reports {

        @Test
        fun `an unbalanced bracket, naming the file and the position`() {
            val result = bf(source = "+[+")

            assertThat(result.error).contains("prog.bf", "']' expected at 3")
            assertThat(result.statusCode).isOne()
        }

        @Test
        fun `output already written before a program was stopped`() {
            // the generated flush only runs on a normal return
            assertThat(bf(source = "++++++++[>++++++++<-]>+.<<.").output).isEqualTo("A")
        }
    }

    @Nested
    inner class Rejects {

        @Test
        fun `a source file that is not there`() {
            val result = bf(File(dir, "absent.bf").path)

            assertThat(result.error).contains("absent.bf", "does not exist")
            assertThat(result.statusCode).isNotZero()
        }

        @Test
        fun `no source file at all`() {
            assertThat(bf().error).contains("missing argument")
        }

        @Test
        fun `an unknown option`() {
            assertThat(bf("--verbose", source = "+").error).contains("no such option", "--verbose")
        }

        @Test
        fun `a limit that is not a number`() {
            assertThat(bf("--memsize=lots", source = "+").error).contains("--memsize", "int")
        }

        @Test
        fun `a tape too small to hold a cell`() {
            assertThat(bf("--memsize=0", source = "+").error).contains("--memsize")
            assertThat(bf("--memsize=-1", source = "+").error).contains("--memsize")
        }

        @Test
        fun `a negative cycle limit`() {
            assertThat(bf("--cycles=-1", source = "+").error).contains("--cycles")
        }
    }

    @Nested
    inner class Help {

        @Test
        fun `lists the argument, both limits and their defaults`() {
            assertThat(BfCommand().test("--help").output).contains(
                "<source>", "the program to run",
                "--memsize", "tape length in cells", "default: 30000",
                "--cycles", "default: ${Int.MAX_VALUE}",
            )
        }
    }
}
