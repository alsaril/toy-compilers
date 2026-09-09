package com.alsaril.math

import com.github.ajalt.clikt.testing.test
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

/**
 * Two commands behind one name. Given --expr and --vars the whole job is known up front,
 * so it answers once per set and says on the way out whether any of them failed. Given
 * --interactive there is always a next line to read, so everything short of running out
 * of input is reported and asked again.
 */
class MainTest {

    private class Invocation(val output: String, val error: String, val statusCode: Int)

    /** the output with the prompts taken out, so a test can be about what was evaluated */
    private val Invocation.printed: String
        get() = output.replace("expr> ", "").replace("vars> ", "")

    private fun math(vararg args: String, input: String = ""): Invocation {
        val stdin = System.`in`
        System.setIn(ByteArrayInputStream(input.toByteArray()))
        try {
            val result = MathCommand().test(args.toList())
            return Invocation(result.stdout, result.stderr, result.statusCode)
        } finally {
            System.setIn(stdin)
        }
    }

    @Nested
    inner class Evaluates {

        @Test
        fun `one line per set of variables, in the order they were given`() {
            val result = math("--expr", "x+1", "--vars", "x=1", "--vars", "x=10", "--vars", "x=2")

            assertThat(result.output).isEqualTo("2.0\n11.0\n3.0\n")
            assertThat(result.error).isEmpty()
            assertThat(result.statusCode).isZero()
        }

        @Test
        fun `several variables within one set`() {
            assertThat(math("--expr", "x*y", "--vars", "x=2;y=3").output).isEqualTo("6.0\n")
        }

        @Test
        fun `a set written with spaces around its separators`() {
            assertThat(math("--expr", "x*y", "--vars", "x = 2 ; y = 3").output).isEqualTo("6.0\n")
        }

        @Test
        fun `an expression naming no variables at all`() {
            assertThat(math("--expr", "1+2", "--vars", "").output).isEqualTo("3.0\n")
        }

        @Test
        fun `nothing when no set was given, having compiled the expression anyway`() {
            val result = math("--expr", "1+2")

            assertThat(result.output).isEmpty()
            assertThat(result.statusCode).isZero()
        }

        @Test
        fun `whatever the arithmetic comes to`() {
            assertThat(math("--expr", "x/y", "--vars", "x=1;y=0").output).isEqualTo("Infinity\n")
            assertThat(math("--expr", "x/y", "--vars", "x=0;y=0").output).isEqualTo("NaN\n")
        }
    }

    /**
     * A set that cannot be evaluated still takes up its line, so the results stay lined up
     * with the sets they came from and the reason goes where it will not be mistaken for one.
     */
    @Nested
    inner class Reports {

        @Test
        fun `a failed set in its own place, with the reason on the error stream`() {
            val result = math("--expr", "x+1", "--vars", "x=1", "--vars", "y=1", "--vars", "x=3")

            assertThat(result.output).isEqualTo("2.0\nfailed\n4.0\n")
            assertThat(result.error).isEqualTo("no variable with name x is found\n")
            assertThat(result.statusCode).isOne()
        }

        @Test
        fun `a set that is not an assignment`() {
            val result = math("--expr", "x", "--vars", "zz")

            assertThat(result.output).isEqualTo("failed\n")
            assertThat(result.error).isEqualTo("'zz' is not an assignment\n")
            assertThat(result.statusCode).isOne()
        }

        @Test
        fun `a value that is not a number`() {
            val result = math("--expr", "x", "--vars", "x=lots")

            assertThat(result.output).isEqualTo("failed\n")
            assertThat(result.error).isEqualTo("'lots' is not a number\n")
        }

        @Test
        fun `every set that failed, not just the first`() {
            val result = math("--expr", "x", "--vars", "zz", "--vars", "x=1", "--vars", "y=1")

            assertThat(result.output).isEqualTo("failed\n1.0\nfailed\n")
            assertThat(result.error).isEqualTo(
                "'zz' is not an assignment\nno variable with name x is found\n"
            )
            assertThat(result.statusCode).isOne()
        }
    }

    @Nested
    inner class Rejects {

        @Test
        fun `no expression to evaluate`() {
            val result = math("--vars", "x=1")

            assertThat(result.error).contains("--expr is required unless --interactive is given")
            assertThat(result.statusCode).isNotZero()
        }

        @Test
        fun `an expression that does not compile, naming the position`() {
            val result = math("--expr", "1+", "--vars", "x=1")

            assertThat(result.error).isEqualTo("could not compile expression: operand expected at 2\n")
            assertThat(result.output).isEmpty()
            assertThat(result.statusCode).isOne()
        }

        @Test
        fun `arguments that only make sense without prompting`() {
            assertThat(math("--interactive", "--expr", "1+2").error)
                .contains("--expr and --vars cannot be used with --interactive")
            assertThat(math("--interactive", "--vars", "x=1").error)
                .contains("--expr and --vars cannot be used with --interactive")
        }

        @Test
        fun `an unknown option`() {
            assertThat(math("--verbose", "--expr", "1+2").error).contains("no such option", "--verbose")
        }
    }

    @Nested
    inner class Interactive {

        @Test
        fun `prompts for the expression and for every line it tries to read`() {
            val result = math("--interactive", input = "x+1\nx=1\n")

            assertThat(result.output).isEqualTo("expr> vars> 2.0\nvars> ")
            assertThat(result.statusCode).isZero()
        }

        @Test
        fun `evaluates the expression once per line`() {
            assertThat(math("--interactive", input = "x*2\nx=1\nx=2\n").printed)
                .isEqualTo("2.0\n4.0\n")
        }

        @Test
        fun `asks again for an expression that does not compile`() {
            val result = math("--interactive", input = "1+\n))\nx+1\nx=1\n")

            assertThat(result.printed).isEqualTo(
                "could not compile expression: operand expected at 2\n" +
                    "could not compile expression: operand expected at 0\n" +
                    "2.0\n"
            )
            assertThat(result.statusCode).isZero()
        }

        @Test
        fun `keeps reading after a line it could not evaluate`() {
            assertThat(math("--interactive", input = "x\nzz\ny=1\nx=1\n").printed).isEqualTo(
                "error: 'zz' is not an assignment\n" +
                    "no variable with name x is found\n" +
                    "1.0\n"
            )
        }

        @Test
        fun `takes a line naming no variable as a map with nothing in it`() {
            assertThat(math("--interactive", input = "1+2\n\n\n").printed).isEqualTo("3.0\n3.0\n")
        }

        @Test
        fun `stops when the input runs out`() {
            val result = math("--interactive", input = "x\nx=1")

            assertThat(result.printed).isEqualTo("1.0\n")
            assertThat(result.statusCode).isZero()
        }

        @Test
        fun `gives up when there is no expression to read at all`() {
            val result = math("--interactive", input = "")

            assertThat(result.output).isEqualTo("expr> ")
            assertThat(result.error).isEqualTo("no expression given\n")
            assertThat(result.statusCode).isOne()
        }

        @Test
        fun `gives up when nothing that was typed ever compiled`() {
            val result = math("--interactive", input = "1+\n")

            assertThat(result.error).isEqualTo("no expression given\n")
            assertThat(result.statusCode).isOne()
        }
    }

    @Nested
    inner class Help {

        @Test
        fun `lists both modes and what each needs`() {
            assertThat(MathCommand().test("--help").output).contains(
                "--interactive",
                "--expr", "required unless --interactive",
                "--vars", "Repeat for a line of output each",
            )
        }
    }
}
