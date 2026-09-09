package com.alsaril.math

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * The entry point prompts for an expression, compiles it once, then evaluates it against
 * a map per line until the input runs out. Only the two things that leave nothing to do -
 * no expression, or one that will not compile - end the run; anything a later line gets
 * wrong is reported and the loop carries on.
 */
class MainTest {

    private class Invocation(val output: String, val error: String, val statusCode: Int)

    /** the output with the prompts taken out, so a test can be about what was evaluated */
    private val Invocation.printed: String
        get() = output.replace("expr> ", "").replace("vars> ", "")

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
    inner class Prompts {

        @Test
        fun `for the expression, then for every variable line it tries to read`() {
            // the last prompt is the one that finds the input has run out
            assertThat(math("x+1\nx=1\n").output).isEqualTo("expr> vars> 2.0\nvars> ")
        }

        @Test
        fun `for the expression alone when nothing follows it`() {
            assertThat(math("1+2\n").output).isEqualTo("expr> vars> ")
        }

        @Test
        fun `for the expression even when there is none to read`() {
            assertThat(math("").output).isEqualTo("expr> ")
        }
    }

    @Nested
    inner class Runs {

        @Test
        fun `compiles the first line and evaluates what follows`() {
            val result = math("x+1\nx=1\n")

            assertThat(result.printed).isEqualTo("2.0\n")
            assertThat(result.error).isEmpty()
            assertThat(result.statusCode).isZero()
        }

        @Test
        fun `evaluates the same expression once per line`() {
            assertThat(math("x*2\nx=1\nx=2\nx=3\n").printed).isEqualTo("2.0\n4.0\n6.0\n")
        }

        @Test
        fun `takes several variables from one line`() {
            assertThat(math("x*y\nx=2;y=3\n").printed).isEqualTo("6.0\n")
        }

        @Test
        fun `allows spaces around the equals sign`() {
            assertThat(math("x*y\nx = 2 ; y = 3\n").printed).isEqualTo("6.0\n")
        }

        @Test
        fun `prints what the expression evaluates to, however it turns out`() {
            assertThat(math("x/y\nx=1;y=0\n").printed).isEqualTo("Infinity\n")
            assertThat(math("x/y\nx=0;y=0\n").printed).isEqualTo("NaN\n")
        }

        @Test
        fun `ignores a trailing separator on a variable line`() {
            assertThat(math("x\nx=1;\n").printed).isEqualTo("1.0\n")
        }

        @Test
        fun `keeps the last value when a line repeats a variable`() {
            assertThat(math("x\nx=1;x=2\n").printed).isEqualTo("2.0\n")
        }
    }

    @Nested
    inner class ReadsAnEmptyLine {

        @Test
        fun `as a map with nothing in it`() {
            assertThat(math("1+2\n\n").printed).isEqualTo("3.0\n")
        }

        @Test
        fun `the same way when it holds only whitespace`() {
            assertThat(math("1+2\n   \n").printed).isEqualTo("3.0\n")
        }

        @Test
        fun `once per line, like any other`() {
            assertThat(math("1+2\n\n\n\n").printed).isEqualTo("3.0\n3.0\n3.0\n")
        }
    }

    @Nested
    inner class Stops {

        @Test
        fun `when the input runs out`() {
            val result = math("x\nx=1\n")

            assertThat(result.printed).isEqualTo("1.0\n")
            assertThat(result.statusCode).isZero()
        }

        @Test
        fun `when the last line has no line break of its own`() {
            val result = math("x\nx=1")

            assertThat(result.printed).isEqualTo("1.0\n")
            assertThat(result.statusCode).isZero()
        }

        @Test
        fun `before evaluating anything when only the expression was given`() {
            val result = math("1+2\n")

            assertThat(result.printed).isEmpty()
            assertThat(result.statusCode).isZero()
        }
    }

    @Nested
    inner class Recovers {

        @Test
        fun `from a variable the line does not give, naming it`() {
            val result = math("x+1\ny=1\nx=1\n")

            assertThat(result.printed).isEqualTo("no variable with name x is found\n2.0\n")
            assertThat(result.statusCode).isZero()
        }

        @Test
        fun `from a line that is not an assignment`() {
            assertThat(math("x\nzz\nx=1\n").printed)
                .isEqualTo("error: 'zz' is not an assignment\n1.0\n")
        }

        @Test
        fun `from a value that is not a number`() {
            assertThat(math("x\nx=lots\nx=1\n").printed)
                .isEqualTo("error: 'lots' is not a number\n1.0\n")
        }

        @Test
        fun `from a value that is not a number, named without the spaces around it`() {
            assertThat(math("x\nx =  lots \nx=1\n").printed)
                .isEqualTo("error: 'lots' is not a number\n1.0\n")
        }

        @Test
        fun `from one failure after another`() {
            assertThat(math("x\nzz\nx=lots\nx=1\n").printed).isEqualTo(
                "error: 'zz' is not an assignment\n" +
                    "error: 'lots' is not a number\n" +
                    "1.0\n"
            )
        }

        @Test
        fun `naming the first variable the line is missing`() {
            assertThat(math("a+b*c\na=1\n").printed)
                .isEqualTo("no variable with name b is found\n")
        }

        @Test
        fun `writing what went wrong where the results go, not to the error stream`() {
            val result = math("x\nzz\n")

            assertThat(result.error).isEmpty()
            assertThat(result.statusCode).isZero()
        }
    }

    @Nested
    inner class Rejects {

        @Test
        fun `an input holding no expression at all`() {
            val result = math("")

            assertThat(result.error).isEqualTo("no expression given\n")
            assertThat(result.statusCode).isOne()
        }

        @Test
        fun `an expression that does not parse, naming the position`() {
            val result = math("1+\nx=1\n")

            assertThat(result.error)
                .isEqualTo("could not compile expression: operand expected at 2\n")
            assertThat(result.printed).isEmpty()
            assertThat(result.statusCode).isOne()
        }
    }
}
