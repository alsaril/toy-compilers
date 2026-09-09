package com.alsaril.math

import com.alsaril.math.BinaryKind.*
import com.alsaril.math.Parser.parse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ParserTest {

    @Nested
    inner class Numbers {

        @Test
        fun `reads a single digit`() {
            assertThat(parse("1")).isEqualTo(Value(1.0f))
        }

        @Test
        fun `reads several digits as one number`() {
            assertThat(parse("42")).isEqualTo(Value(42.0f))
            assertThat(parse("1024")).isEqualTo(Value(1024.0f))
        }

        @Test
        fun `reads a decimal point`() {
            assertThat(parse("1.5")).isEqualTo(Value(1.5f))
            assertThat(parse("0.25")).isEqualTo(Value(0.25f))
        }

        @Test
        fun `accepts a trailing decimal point`() {
            assertThat(parse("1.")).isEqualTo(Value(1.0f))
        }

        @Test
        fun `ignores leading zeroes`() {
            assertThat(parse("007")).isEqualTo(Value(7.0f))
        }

        @Test
        fun `reads a number that starts with a decimal point`() {
            assertThat(parse(".5")).isEqualTo(Value(0.5f))
            assertThat(parse(".25")).isEqualTo(Value(0.25f))
        }

        @Test
        fun `takes a number starting with a point as an operand like any other`() {
            assertThat(parse(".25+1")).isEqualTo(Op(ADD, Value(0.25f), Value(1.0f)))
            assertThat(parse("2*.5")).isEqualTo(Op(MUL, Value(2.0f), Value(0.5f)))
            assertThat(parse("(.5)")).isEqualTo(Value(0.5f))
        }

        @Test
        fun `takes only the first decimal point of a number`() {
            // the scan stops at the second point, which then reads as a second operand
            assertThatIllegalArgumentException()
                .isThrownBy { parse("1.2.3") }
                .withMessage("operator expected at 3")
            assertThatIllegalArgumentException()
                .isThrownBy { parse("1..2") }
                .withMessage("operator expected at 2")
            assertThatIllegalArgumentException()
                .isThrownBy { parse(".5.5") }
                .withMessage("operator expected at 2")
        }

        @Test
        fun `rejects a decimal point with no digits of its own`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parse(".") }
                .withMessage("unexpected '.' at 0")
            assertThatIllegalArgumentException()
                .isThrownBy { parse("1+.") }
                .withMessage("unexpected '.' at 2")
        }
    }

    @Nested
    inner class Variables {

        @Test
        fun `reads a one letter name`() {
            assertThat(parse("x")).isEqualTo(Var("x"))
        }

        @Test
        fun `reads a name of several letters`() {
            assertThat(parse("width")).isEqualTo(Var("width"))
        }

        @Test
        fun `keeps names of different case apart`() {
            assertThat(parse("X")).isNotEqualTo(parse("x"))
        }

        @Test
        fun `mixes variables and numbers in one expression`() {
            assertThat(parse("x*y+1")).isEqualTo(
                Op(ADD, Op(MUL, Var("x"), Var("y")), Value(1.0f))
            )
        }

        @Test
        fun `does not take a digit as part of a name`() {
            // "x1" is a name and a number side by side, which is two operands and no operator
            assertThatIllegalArgumentException()
                .isThrownBy { parse("x1") }
                .withMessage("operator expected at 1")
        }
    }

    @Nested
    inner class Operators {

        @Test
        fun `maps every operator character`() {
            assertThat(parse("1+2")).isEqualTo(Op(ADD, Value(1.0f), Value(2.0f)))
            assertThat(parse("1-2")).isEqualTo(Op(SUB, Value(1.0f), Value(2.0f)))
            assertThat(parse("1*2")).isEqualTo(Op(MUL, Value(1.0f), Value(2.0f)))
            assertThat(parse("1/2")).isEqualTo(Op(DIV, Value(1.0f), Value(2.0f)))
        }
    }

    @Nested
    inner class Precedence {

        @Test
        fun `binds multiplication tighter than addition`() {
            assertThat(parse("1+2*3")).isEqualTo(
                Op(ADD, Value(1.0f), Op(MUL, Value(2.0f), Value(3.0f)))
            )
            assertThat(parse("1*2+3")).isEqualTo(
                Op(ADD, Op(MUL, Value(1.0f), Value(2.0f)), Value(3.0f))
            )
        }

        @Test
        fun `binds division tighter than subtraction`() {
            assertThat(parse("1-2/3")).isEqualTo(
                Op(SUB, Value(1.0f), Op(DIV, Value(2.0f), Value(3.0f)))
            )
            assertThat(parse("1/2-3")).isEqualTo(
                Op(SUB, Op(DIV, Value(1.0f), Value(2.0f)), Value(3.0f))
            )
        }

        @Test
        fun `keeps operators of equal priority in reading order`() {
            assertThat(parse("1+2-3")).isEqualTo(
                Op(SUB, Op(ADD, Value(1.0f), Value(2.0f)), Value(3.0f))
            )
            assertThat(parse("1*2/3")).isEqualTo(
                Op(DIV, Op(MUL, Value(1.0f), Value(2.0f)), Value(3.0f))
            )
        }
    }

    @Nested
    inner class Associativity {

        @Test
        fun `groups addition to the left`() {
            assertThat(parse("1+2+3")).isEqualTo(
                Op(ADD, Op(ADD, Value(1.0f), Value(2.0f)), Value(3.0f))
            )
        }

        @Test
        fun `groups subtraction to the left`() {
            // right grouping would make 1-2-3 read as 1-(2-3), which is 2 rather than -4
            assertThat(parse("1-2-3")).isEqualTo(
                Op(SUB, Op(SUB, Value(1.0f), Value(2.0f)), Value(3.0f))
            )
        }

        @Test
        fun `groups division to the left`() {
            assertThat(parse("8/4/2")).isEqualTo(
                Op(DIV, Op(DIV, Value(8.0f), Value(4.0f)), Value(2.0f))
            )
        }
    }

    @Nested
    inner class Brackets {

        @Test
        fun `overrides precedence`() {
            assertThat(parse("(1+2)*3")).isEqualTo(
                Op(MUL, Op(ADD, Value(1.0f), Value(2.0f)), Value(3.0f))
            )
            assertThat(parse("2*(3+4)")).isEqualTo(
                Op(MUL, Value(2.0f), Op(ADD, Value(3.0f), Value(4.0f)))
            )
        }

        @Test
        fun `groups both sides of an operator`() {
            assertThat(parse("(1+2)*(3-4)")).isEqualTo(
                Op(
                    MUL,
                    Op(ADD, Value(1.0f), Value(2.0f)),
                    Op(SUB, Value(3.0f), Value(4.0f)),
                )
            )
        }

        @Test
        fun `nests`() {
            assertThat(parse("((1+2)*3)+4")).isEqualTo(
                Op(
                    ADD,
                    Op(MUL, Op(ADD, Value(1.0f), Value(2.0f)), Value(3.0f)),
                    Value(4.0f),
                )
            )
        }

        @Test
        fun `changes nothing when they are redundant`() {
            assertThat(parse("((1))")).isEqualTo(Value(1.0f))
            assertThat(parse("(1)+(2)")).isEqualTo(parse("1+2"))
            assertThat(parse("(1+2*3)")).isEqualTo(parse("1+2*3"))
        }
    }

    @Nested
    inner class Whitespace {

        @Test
        fun `is ignored around operators and operands`() {
            assertThat(parse(" 1 + 2 * 3 ")).isEqualTo(parse("1+2*3"))
            assertThat(parse("( 1 + 2 ) * 3")).isEqualTo(parse("(1+2)*3"))
        }

        @Test
        fun `still separates two operands`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parse("1 2") }
                .withMessage("operator expected at 2")
        }
    }

    @Test
    fun `parses an expression using every rule at once`() {
        assertThat(parse("2*(3+4)-1/2")).isEqualTo(
            Op(
                SUB,
                Op(MUL, Value(2.0f), Op(ADD, Value(3.0f), Value(4.0f))),
                Op(DIV, Value(1.0f), Value(2.0f)),
            )
        )
    }

    @Nested
    inner class Rejects {

        @Test
        fun `an empty expression`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parse("") }
                .withMessage("operand expected at 0")
            assertThatIllegalArgumentException()
                .isThrownBy { parse("   ") }
                .withMessage("operand expected at 3")
        }

        @Test
        fun `a character that is not part of the language`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parse("1 # 2") }
                .withMessage("unexpected '#' at 2")
            assertThatIllegalArgumentException()
                .isThrownBy { parse("1^2") }
                .withMessage("unexpected '^' at 1")
        }

        @Test
        fun `a closing bracket that opens nothing`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parse("1)") }
                .withMessage("unexpected ')' at 1")
            assertThatIllegalArgumentException()
                .isThrownBy { parse("(1+2))") }
                .withMessage("unexpected ')' at 5")
        }

        @Test
        fun `a closing bracket reached only after reducing`() {
            // the operators before it reduce away first, and the bracket is missing behind them
            assertThatIllegalArgumentException()
                .isThrownBy { parse("1+2)") }
                .withMessage("unexpected ')' at 3")
        }

        @Test
        fun `a bracket that is never closed`() {
            // the ')' belongs at the end, which is where the index points
            assertThatIllegalArgumentException()
                .isThrownBy { parse("(1") }
                .withMessage("')' expected at 2")
            assertThatIllegalArgumentException()
                .isThrownBy { parse("(1+2") }
                .withMessage("')' expected at 4")
            assertThatIllegalArgumentException()
                .isThrownBy { parse("((1+2)") }
                .withMessage("')' expected at 6")
        }
    }

    /**
     * Operands and operators alternate, so whichever of the two is missing is known
     * before the parser reaches the end, and is named at the index it was due.
     */
    @Nested
    inner class Reports {

        @Test
        fun `a missing operand after a trailing operator`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parse("1+") }
                .withMessage("operand expected at 2")
            assertThatIllegalArgumentException()
                .isThrownBy { parse("1*2-") }
                .withMessage("operand expected at 4")
        }

        @Test
        fun `a missing operand before a leading operator, so there is no unary minus`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parse("-1") }
                .withMessage("operand expected at 0")
            assertThatIllegalArgumentException()
                .isThrownBy { parse("2*-3") }
                .withMessage("operand expected at 2")
        }

        @Test
        fun `a missing operand where a closing bracket appears instead`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parse("()") }
                .withMessage("operand expected at 1")

            // a lone ')' is a missing operand before it is an unmatched bracket
            assertThatIllegalArgumentException()
                .isThrownBy { parse(")") }
                .withMessage("operand expected at 0")
        }

        @Test
        fun `a missing operator between two operands`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parse("1 2") }
                .withMessage("operator expected at 2")
            assertThatIllegalArgumentException()
                .isThrownBy { parse("x y") }
                .withMessage("operator expected at 2")
            assertThatIllegalArgumentException()
                .isThrownBy { parse("(1 2") }
                .withMessage("operator expected at 3")
        }

        @Test
        fun `a missing operator before a bracket standing next to an operand`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parse("(1)(2)") }
                .withMessage("operator expected at 3")
            assertThatIllegalArgumentException()
                .isThrownBy { parse("2(3)") }
                .withMessage("operator expected at 1")
        }
    }

    @Test
    fun `reports every malformed expression the same way`() {
        val malformed = listOf(
            "", "   ", "()", "1 2", "x y", "x1", "2(3)", "(1)(2)", "(1 2",
            "-1", "2*-3", "1+", "1*2-", "(1", "(1+2", "1)", "1+2)", "(1+2))",
            ")", "1^2", "1 # 2", "1.2.3", ".", "..", ".+1", "1+.",
        )

        malformed.forEach { source ->
            assertThatIllegalArgumentException()
                .describedAs("parsing %s", source)
                .isThrownBy { parse(source) }
                .withMessageMatching(".+ at \\d+")
        }
    }
}
