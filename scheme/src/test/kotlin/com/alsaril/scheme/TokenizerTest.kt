package com.alsaril.scheme

import com.alsaril.scheme.tokenizer.*
import com.alsaril.scheme.tokenizer.BracketToken.CloseBracketToken
import com.alsaril.scheme.tokenizer.BracketToken.OpenBracketToken
import com.alsaril.scheme.tokenizer.Tokenizer.tokenize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class TokenizerTest {
    @ParameterizedTest
    @MethodSource("simple")
    fun `tokenizes simple expression`(input: String, expected: List<Token>) {
        // when
        val output = tokenize(input)

        // then
        assertThat(output).containsExactlyElementsOf(expected)
    }

    @ParameterizedTest
    @MethodSource("negative numbers")
    fun `tokenizes negative numbers`(input: String, expected: List<Token>) {
        // when
        val output = tokenize(input)

        // then
        assertThat(output).containsExactlyElementsOf(expected)
    }

    @ParameterizedTest
    @MethodSource("spaces")
    fun `correctly handles spaces`(input: String, expected: List<Token>) {
        // when
        val output = tokenize(input)

        // then
        assertThat(output).containsExactlyElementsOf(expected)
    }

    @ParameterizedTest
    @MethodSource("unary")
    fun `correctly handles unary plus and minus`(input: String, expected: List<Token>) {
        // when
        val output = tokenize(input)

        // then
        assertThat(output).containsExactlyElementsOf(expected)
    }

    @ParameterizedTest
    @MethodSource("symbols")
    fun `correctly handles symbol names`(input: String, expected: List<Token>) {
        // when
        val output = tokenize(input)

        // then
        assertThat(output).containsExactlyElementsOf(expected)
    }

    @ParameterizedTest
    @MethodSource("brackets")
    fun `correctly handles brackets`(input: String, expected: List<Token>) {
        // when
        val output = tokenize(input)

        // then
        assertThat(output).containsExactlyElementsOf(expected)
    }

    companion object {
        @JvmStatic
        fun simple() = listOf(
            arguments("", listOf<Token>()),
            arguments("2", listOf(ConstantToken(2))),
            arguments("34", listOf(ConstantToken(34))),
            arguments("  123", listOf(ConstantToken(123))),
            arguments("hello", listOf(SymbolToken("hello"))),
            arguments(" world", listOf(SymbolToken("world"))),
            arguments(" 4+)'.", listOf(ConstantToken(4), SymbolToken("+"), CloseBracketToken, QuoteToken, DotToken)),
        )

        @JvmStatic
        fun `negative numbers`() = listOf(
            arguments("-2", listOf(ConstantToken(-2))),
            arguments("-2345", listOf(ConstantToken(-2345))),
            arguments("-53 - -123", listOf(ConstantToken(-53), SymbolToken("-"), ConstantToken(-123))),
            arguments("-2 - 2", listOf(ConstantToken(-2), SymbolToken("-"), ConstantToken(2))),
        )

        @JvmStatic
        fun spaces() = listOf(
            arguments("      ", listOf<Token>()),
            arguments("  4 +  ", listOf(ConstantToken(4), SymbolToken("+"))),
            arguments(" aba  foo caba   ", listOf(SymbolToken("aba"), SymbolToken("foo"), SymbolToken("caba"))),
        )

        @JvmStatic
        fun unary() = listOf(
            arguments("+4", listOf(ConstantToken(4))),
            arguments("+67", listOf(ConstantToken(67))),
            arguments(
                "+1 -  -2 ++3",
                listOf(ConstantToken(1), SymbolToken("-"), ConstantToken(-2), SymbolToken("+"), ConstantToken(3))
            ),
        )

        @JvmStatic
        fun symbols() = listOf(
            arguments("foo bar zog-zog?", listOf(SymbolToken("foo"), SymbolToken("bar"), SymbolToken("zog-zog?"))),
            arguments("baz-15 foo+15", listOf(SymbolToken("baz-15"), SymbolToken("foo"), ConstantToken(15))),
            arguments(
                "<=> *42. #hash-tag' 'hi!.##", listOf(
                    SymbolToken("<=>"), SymbolToken("*42"), DotToken,
                    SymbolToken("#hash-tag"), QuoteToken, QuoteToken, SymbolToken("hi!"), DotToken, SymbolToken("##")
                )
            ),
        )

        @JvmStatic
        fun brackets() = listOf(
            arguments("( ()", listOf(OpenBracketToken, OpenBracketToken, CloseBracketToken)),
            arguments(
                "(-2 3 aba)",
                listOf(OpenBracketToken, ConstantToken(-2), ConstantToken(3), SymbolToken("aba"), CloseBracketToken)
            ),
            arguments(
                ")aba(caba)",
                listOf(CloseBracketToken, SymbolToken("aba"), OpenBracketToken, SymbolToken("caba"), CloseBracketToken)
            ),
            arguments(
                "(.-25##-15)'-8", listOf(
                    OpenBracketToken,
                    DotToken,
                    ConstantToken(-25),
                    SymbolToken("##-15"),
                    CloseBracketToken,
                    QuoteToken,
                    ConstantToken(-8)
                )
            ),
        )
    }
}
