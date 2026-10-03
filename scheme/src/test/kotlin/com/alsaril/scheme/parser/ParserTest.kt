package com.alsaril.scheme.parser

import com.alsaril.scheme.SchemeSyntaxException
import com.alsaril.scheme.parser.Parser.parse
import com.alsaril.scheme.tokenizer.Tokenizer.tokenize
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

class ParserTest {
    @ParameterizedTest
    @MethodSource("atoms", "lists")
    fun `parses atoms and lists`(input: String, expected: Node) {
        assertThat(parse(tokenize(input))).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "$$ | unexpected end of input",
        "' | unexpected end of input",
        "( | unexpected end of input, expected ')' to close list opened at index 0",
        "(1 | unexpected end of input, expected ')' to close list opened at index 0",
        "((1) | unexpected end of input, expected ')' to close list opened at index 0",
        "(1 . | unexpected end of input",
        "(1 . () | unexpected end of input, expected ')' after dotted pair tail",
        ") | unexpected ')' at index 0",
        ")(1) | unexpected ')' at index 0",
        "(1 . ) | unexpected ')' at index 3",
        "(- 3 (+ 2 .)) | unexpected ')' at index 7",
        ". | unexpected '.' at index 0",
        "( . | unexpected '.' at index 1",
        "(. 3) | unexpected '.' at index 1",
        "(1 . 2 3) | expected ')' after dotted pair tail at index 4",
        "(1 . 2 . 3) | expected ')' after dotted pair tail at index 4",
        "- 5 | unexpected '5' after the end of the expression at index 1",
        "a b | unexpected 'b' after the end of the expression at index 1",
        "(1)) | unexpected ')' after the end of the expression at index 3",
        "1 (2) | unexpected '(' after the end of the expression at index 1",
        "1 . 2 | unexpected '.' after the end of the expression at index 1",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `rejects malformed input`(input: String, message: String) {
        assertThatExceptionOfType(SchemeSyntaxException::class.java)
            .isThrownBy { parse(tokenize(input)) }
            .withMessage(message)
    }

    companion object {
        private fun properList(vararg items: Node): Node =
            items.foldRight(Null as Node) { item, acc -> Cell(item, acc) }

        private fun dottedList(vararg items: Node, tail: Node): Node =
            items.foldRight(tail) { item, acc -> Cell(item, acc) }

        @JvmStatic
        fun atoms() = listOf(
            arguments("5", Number(5)),
            arguments("+", Symbol("+")),
            arguments(" #foo", Symbol("#foo")),
            arguments("bar! ", Symbol("bar!")),
            arguments(" baz-baz ", Symbol("baz-baz")),
            arguments("#t", Special("#t")),
            arguments("#f", Special("#f")),
            arguments("define", Special("define")),
            arguments("set!", Special("set!")),
            arguments("if", Special("if")),
            arguments("and", Special("and")),
            arguments("or", Special("or")),
            arguments("quote", Special("quote")),
            arguments("lambda", Special("lambda")),
            arguments("#true", Symbol("#true")),
            arguments("if?", Symbol("if?")),
            arguments("set", Symbol("set")),
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
            arguments("'x", properList(Special("quote"), Symbol("x"))),
            arguments("(quote x)", properList(Special("quote"), Symbol("x"))),
            arguments("'(if #f)", properList(Special("quote"), properList(Special("if"), Special("#f")))),
        )
    }
}
