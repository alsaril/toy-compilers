package com.alsaril.scheme

import com.alsaril.scheme.parser.*
import com.alsaril.scheme.parser.Number
import com.alsaril.scheme.parser.Parser.parse
import com.alsaril.scheme.tokenizer.Tokenizer.tokenize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

class ParserTest {
    @Test
    fun `throws on empty input`() {
        assertThrows<IllegalArgumentException> { parse(emptyList()) }
    }

    @ParameterizedTest
    @MethodSource("simple")
    fun `parses numbers`(input: String, expected: Node) {
        // when
        val output = parse(tokenize(input))

        // then
        assertThat(output).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("lists")
    fun `parses lists`(input: String, expected: Node) {
        // when
        val output = parse(tokenize(input))

        // then
        assertThat(output).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(",
        ")",
        " . ",
        "(1",
        "(1 .",
        "( .",
        "(1 . ()",
        "(1 . )",
        "(- 3 (+ 2 .))",
        "(1 . 2 3)",
        "(. 3)",
        "- 5",
        "(1 . 2 . 3)",
    )
    fun `throws on invalid lists`(input: String) {
        // when / then
        assertThrows<IllegalArgumentException> { parse(tokenize(input)) }
    }

    @ParameterizedTest
    @MethodSource("errorMessages")
    fun `throws with a descriptive message`(input: String, expectedMessage: String) {
        // when
        val exception = assertThrows<IllegalArgumentException> { parse(tokenize(input)) }

        // then
        assertThat(exception.message).isEqualTo(expectedMessage)
    }

    companion object {
        private fun properList(vararg items: Node): Node =
            items.foldRight(Null as Node) { item, acc -> Cell(item, acc) }

        private fun dottedList(vararg items: Node, tail: Node): Node =
            items.foldRight(tail) { item, acc -> Cell(item, acc) }

        @JvmStatic
        fun simple() = listOf(
            arguments("5", Number(5)),
            arguments("+", Symbol("+")),
            arguments(" #foo", Symbol("#foo")),
            arguments("bar! ", Symbol("bar!")),
            arguments(" baz-baz ", Symbol("baz-baz")),
        )

        @JvmStatic
        fun lists() = listOf(
            arguments("()", Null),
            arguments("(1 . 2)", dottedList(Number(1), tail = Number(2))),
            arguments("(1 2)", properList(Number(1), Number(2))),
            arguments("(+ 1 29)", properList(Symbol("+"), Number(1), Number(29))),
            arguments("(1 -2 . +3)", dottedList(Number(1), Number(-2), tail = Number(3))),
            arguments(
                "(1 (abacaba 4 -22) . 3)",
                dottedList(
                    Number(1),
                    properList(Symbol("abacaba"), Number(4), Number(-22)),
                    tail = Number(3)
                )
            ),
            arguments("(1 . ())", dottedList(Number(1), tail = Null)),
            arguments("(1 . (-2 . ()))", dottedList(Number(1), tail = dottedList(Number(-2), tail = Null))),
            arguments(
                "(-14 25 (3 41) (()))",
                properList(
                    Number(-14),
                    Number(25),
                    properList(Number(3), Number(41)),
                    properList(Null)
                )
            ),
            arguments(
                "(+ 1 -2 (- 31 +4))",
                properList(
                    Symbol("+"),
                    Number(1),
                    Number(-2),
                    properList(Symbol("-"), Number(31), Number(4))
                )
            ),
            arguments("(() () ())", properList(Null, Null, Null)),
            arguments("(() () . ())", dottedList(Null, Null, tail = Null)),
            arguments(
                "(() (hello-world) -42)",
                properList(Null, properList(Symbol("hello-world")), Number(-42))
            ),
            arguments(
                "(aba! (#caba 2 (1) . 4) (() 3 2 ()))",
                properList(
                    Symbol("aba!"),
                    dottedList(Symbol("#caba"), Number(2), properList(Number(1)), tail = Number(4)),
                    properList(Null, Number(3), Number(2), Null)
                )
            ),
            arguments(
                "(1 2 (3 . 4) 5)",
                properList(Number(1), Number(2), dottedList(Number(3), tail = Number(4)), Number(5))
            ),
        )

        @JvmStatic
        fun errorMessages() = listOf(
            arguments("", "unexpected end of input"),
            arguments("(1", "unexpected end of input, expected ')' to close list opened at index 0"),
            arguments("(. 3)", "unexpected token DotToken at index 1"),
            arguments(")", "unexpected ')' at index 0"),
            arguments("(1 . 2 3)", "expected ')' after dotted pair tail at index 4"),
            arguments("- 5", "unexpected trailing token ConstantToken(value=5) at index 1"),
            arguments("(1 . 2 . 3)", "expected ')' after dotted pair tail at index 4"),
        )
    }
}
