package com.alsaril.scheme

import com.github.ajalt.clikt.testing.test
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class MainTest {

    private class Invocation(val output: String, val error: String, val statusCode: Int)

    /** the output with the prompts taken out, so a test can be about what was evaluated */
    private val Invocation.printed: String
        get() = output.replace("scheme> ", "")

    private fun scheme(vararg args: String, input: String = ""): Invocation {
        val stdin = System.`in`
        System.setIn(ByteArrayInputStream(input.toByteArray()))
        try {
            val result = SchemeCommand().test(args.toList())
            return Invocation(result.stdout, result.stderr, result.statusCode)
        } finally {
            System.setIn(stdin)
        }
    }

    @Nested
    inner class Evaluates {

        @Test
        fun `prompts for every line it tries to read`() {
            val result = scheme(input = "1\n2\n")

            assertThat(result.output).isEqualTo("scheme> 1\nscheme> 2\nscheme> ")
            assertThat(result.error).isEmpty()
            assertThat(result.statusCode).isZero()
        }

        @Test
        fun `prints the value of each line`() {
            assertThat(scheme(input = "(+ 1 2)\n'(a . b)\n#f\ncar\n").printed)
                .isEqualTo("3\n(a . b)\n#f\n#<procedure>\n")
        }

        @Test
        fun `keeps definitions for the lines that follow`() {
            assertThat(scheme(input = "(define x 5)\n(define (square n) (* n n))\n(square x)\n").printed)
                .isEqualTo("25\n")
        }

        @Test
        fun `prints nothing for an unspecified value`() {
            assertThat(scheme(input = "(define x 1)\n(set! x 2)\n(if #f #f)\nx\n").printed).isEqualTo("2\n")
        }

        @Test
        fun `prints an unspecified value inside a list`() {
            assertThat(scheme(input = "(list (if #f #f))\n").printed).isEqualTo("(#<unspecified>)\n")
        }

        @Test
        fun `skips blank lines`() {
            val result = scheme(input = "\n   \n1\n")

            assertThat(result.output).isEqualTo("scheme> scheme> scheme> 1\nscheme> ")
        }

        @Test
        fun `stops when the input runs out`() {
            val result = scheme(input = "(+ 1 2)")

            assertThat(result.printed).isEqualTo("3\n")
            assertThat(result.statusCode).isZero()
        }

        @Test
        fun `stops at once when there is no input at all`() {
            val result = scheme(input = "")

            assertThat(result.output).isEqualTo("scheme> ")
            assertThat(result.statusCode).isZero()
        }
    }

    @Nested
    inner class Reports {

        @Test
        fun `a line that does not compile`() {
            assertThat(scheme(input = "(+ 1\n(if)\n").printed).isEqualTo(
                "syntax error: unexpected end of input, expected ')' to close list opened at index 0\n" +
                    "syntax error: if: expected 2 or 3 operands, got 0 in (if)\n"
            )
        }

        @Test
        fun `a symbol that is not defined`() {
            assertThat(scheme(input = "x\n(set! x 1)\n").printed)
                .isEqualTo("name error: x is not defined\nname error: set!: x is not defined\n")
        }

        @Test
        fun `a line that fails while running`() {
            assertThat(scheme(input = "(car 1)\n(1 2)\n").printed)
                .isEqualTo("runtime error: car: expected a pair, got 1\nruntime error: 1 is not a procedure\n")
        }

        @Test
        fun `recursion that runs out of stack`() {
            assertThat(scheme(input = "(define (f n) (+ 1 (f n)))\n(f 1)\n").printed)
                .isEqualTo("runtime error: stack overflow\n")
        }

        @Test
        fun `an expression nested too deeply to compile`() {
            val nested = "(+ 1 ".repeat(20_000) + "0" + ")".repeat(20_000)

            assertThat(scheme(input = "(define x 1)\n$nested\nx\n").printed)
                .isEqualTo("compile error: stack overflow\n1\n")
        }

        @Test
        fun `and keeps reading, with the environment as the failed line left it`() {
            val result = scheme(input = "(define x 1)\n(car x)\n)\n(define y (car x))\ny\nx\n")

            assertThat(result.printed).isEqualTo(
                "runtime error: car: expected a pair, got 1\n" +
                    "syntax error: unexpected ')' at index 0\n" +
                    "runtime error: car: expected a pair, got 1\n" +
                    "name error: y is not defined\n" +
                    "1\n"
            )
            assertThat(result.error).isEmpty()
            assertThat(result.statusCode).isZero()
        }
    }

    @Nested
    inner class Rejects {

        @Test
        fun `an unknown option`() {
            assertThat(scheme("--verbose").error).contains("no such option", "--verbose")
        }
    }

    @Nested
    inner class Help {

        @Test
        fun `says what the command does`() {
            assertThat(SchemeCommand().test("--help").output)
                .contains("Read Scheme expressions a line at a time")
        }
    }
}
