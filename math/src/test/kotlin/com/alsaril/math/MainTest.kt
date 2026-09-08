package com.alsaril.math

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * The entry point reads an expression, compiles it once, then evaluates it against a map
 * per line until the input runs out. Anything raised on the way out is turned into one
 * line on standard error and a non-zero status, which is what the Rejects group checks.
 */
class MainTest {

    private class Invocation(val output: String, val error: String, val statusCode: Int)

    private fun math(input: String): Invocation {
        val written = ByteArrayOutputStream()
        val failed = ByteArrayOutputStream()
        val out = System.out
        val err = System.err
        val stdin = System.`in`
        System.setOut(PrintStream(written, true))
        System.setErr(PrintStream(failed, true))
        System.setIn(ByteArrayInputStream(input.toByteArray()))
        try {
            // run rather than main, which would take the test jvm down with it
            val statusCode = run()
            return Invocation(
                String(written.toByteArray()),
                String(failed.toByteArray()),
                statusCode,
            )
        } finally {
            System.setOut(out)
            System.setErr(err)
            System.setIn(stdin)
        }
    }

    @Nested
    inner class Runs {

        @Test
        fun `compiles the first line and evaluates what follows`() {
            val result = math("x+1\nx=1\n")

            assertThat(result.output).isEqualTo("2.0\n")
            assertThat(result.error).isEmpty()
            assertThat(result.statusCode).isZero()
        }

        @Test
        fun `evaluates the same expression once per line`() {
            assertThat(math("x*2\nx=1\nx=2\nx=3\n").output).isEqualTo("2.0\n4.0\n6.0\n")
        }

        @Test
        fun `takes several variables from one line`() {
            assertThat(math("x*y\nx=2;y=3\n").output).isEqualTo("6.0\n")
        }

        @Test
        fun `prints what the expression evaluates to, however it turns out`() {
            assertThat(math("x/y\nx=1;y=0\n").output).isEqualTo("Infinity\n")
            assertThat(math("x/y\nx=0;y=0\n").output).isEqualTo("NaN\n")
        }

        @Test
        fun `ignores a trailing separator on a variable line`() {
            assertThat(math("x\nx=1;\n").output).isEqualTo("1.0\n")
        }

        @Test
        fun `keeps the last value when a line repeats a variable`() {
            assertThat(math("x\nx=1;x=2\n").output).isEqualTo("2.0\n")
        }
    }

    @Nested
    inner class ReadsAnEmptyLine {

        @Test
        fun `as a map with nothing in it`() {
            assertThat(math("1+2\n\n").output).isEqualTo("3.0\n")
        }

        @Test
        fun `the same way when it holds only whitespace`() {
            assertThat(math("1+2\n   \n").output).isEqualTo("3.0\n")
        }

        @Test
        fun `once per line, like any other`() {
            assertThat(math("1+2\n\n\n\n").output).isEqualTo("3.0\n3.0\n3.0\n")
        }
    }

    @Nested
    inner class Stops {

        @Test
        fun `when the input runs out`() {
            val result = math("x\nx=1\n")

            assertThat(result.output).isEqualTo("1.0\n")
            assertThat(result.statusCode).isZero()
        }

        @Test
        fun `when the last line has no line break of its own`() {
            val result = math("x\nx=1")

            assertThat(result.output).isEqualTo("1.0\n")
            assertThat(result.statusCode).isZero()
        }

        @Test
        fun `before evaluating anything when only the expression was given`() {
            val result = math("1+2\n")

            assertThat(result.output).isEmpty()
            assertThat(result.statusCode).isZero()
        }
    }

    @Nested
    inner class Rejects {

        @Test
        fun `an expression that does not parse, naming the position`() {
            val result = math("1+\n")

            assertThat(result.error).isEqualTo("Error: operand expected at 2\n")
            assertThat(result.output).isEmpty()
            assertThat(result.statusCode).isOne()
        }

        @Test
        fun `an input holding no expression at all`() {
            assertThat(math("").error).isEqualTo("Error: no expression given\n")
            assertThat(math("").statusCode).isOne()
        }

        @Test
        fun `a variable the expression asks for and the line does not give`() {
            assertThat(math("x+1\ny=1\n").error)
                .isEqualTo("Error: the expression uses a variable the line does not give a value\n")
            assertThat(math("x+1\n\n").error)
                .contains("does not give a value")
            assertThat(math("x+1\ny=1\n").statusCode).isOne()
        }

        @Test
        fun `a variable line that is not an assignment, naming the part at fault`() {
            assertThat(math("x\nx\n").error).isEqualTo("Error: 'x' is not an assignment\n")

            // and the same for a part carrying more than one equals sign
            assertThat(math("x\nx=1=2\n").error)
                .isEqualTo("Error: 'x=1=2' is not an assignment\n")
        }

        @Test
        fun `a value that is not a number`() {
            assertThat(math("x\nx=lots\n").error).isEqualTo("Error: 'lots' is not a number\n")
            assertThat(math("x\nx=lots\n").statusCode).isOne()
        }
    }

    @Nested
    inner class Reports {

        @Test
        fun `everything evaluated before a line brought the run down`() {
            val result = math("x\nx=1\nx=2\nx=oops\n")

            assertThat(result.output).isEqualTo("1.0\n2.0\n")
            assertThat(result.error).isEqualTo("Error: 'oops' is not a number\n")
            assertThat(result.statusCode).isOne()
        }
    }
}
