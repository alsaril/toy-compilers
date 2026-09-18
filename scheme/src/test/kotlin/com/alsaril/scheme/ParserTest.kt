package com.alsaril.scheme

import com.alsaril.scheme.parser.*
import com.alsaril.scheme.parser.Number
import com.alsaril.scheme.parser.Parser.parse
import com.alsaril.scheme.tokenizer.Tokenizer.tokenize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

class ParserTest {
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
    )
    fun `throws on invalid lists`(input: String) {
        // when / then
        assertThrows<IllegalArgumentException> { parse(tokenize(input)) }
    }

    companion object {
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
            arguments("(1 . 2)", Cell(Number(1), Number(2))),
            arguments("(1 2)", Cell(Number(1), Cell(Number(2), Null))),
            arguments("(+ 1 29)", Cell(Symbol("+"), Cell(Number(1), Cell(Number(29), Null)))),
            arguments("(1 -2 . +3)", Cell(Number(1), Cell(Number(-2), Number(3)))),
            arguments(
                "(1 (abacaba 4 -22) . 3)",
                Cell(Number(1), Cell(Cell(Symbol("abacaba"), Cell(Number(4), Cell(Number(-22), Null))), Number(3)))
            ),
            arguments("(1 . ())", Cell(Number(1), Null)),
            arguments("(1 . (-2 . ()))", Cell(Number(1), Cell(Number(-2), Null))),
            arguments(
                "(-14 25 (3 41) (()))",
                Cell(
                    Number(-14),
                    Cell(Number(25), Cell(Cell(Number(3), Cell(Number(41), Null)), Cell(Cell(Null, Null), Null)))
                )
            ),
            arguments(
                "(+ 1 -2 (- 31 +4))",
                Cell(
                    Symbol("+"),
                    Cell(
                        Number(1),
                        Cell(Number(-2), Cell(Cell(Symbol("-"), Cell(Number(31), Cell(Number(4), Null))), Null))
                    )
                )
            ),
            arguments("(() () ())", Cell(Null, Cell(Null, Cell(Null, Null)))),
            arguments("(() () . ())", Cell(Null, Cell(Null, Null))),
            arguments(
                "(() (hello-world) -42)",
                Cell(Null, Cell(Cell(Symbol("hello-world"), Null), Cell(Number(-42), Null)))
            ),
            arguments(
                "(aba! (#caba 2 (1) . 4) (() 3 2 ()))", Cell(
                    Symbol("aba!"), Cell(
                        Cell(
                            Symbol("#caba"), Cell(
                                Number(2), Cell(Cell(Number(1), Null), Number(4))
                            )
                        ), Cell(
                            Cell(
                                Null, Cell(
                                    Number(3), Cell(
                                        Number(2), Cell(Null, Null)
                                    )
                                )
                            ), Null
                        )
                    )
                )
            ),
        )
    }
}
