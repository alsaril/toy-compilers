package com.alsaril.scheme

import com.alsaril.scheme.tokenizer.*
import com.alsaril.scheme.tokenizer.BracketToken.CloseBracketToken
import com.alsaril.scheme.tokenizer.BracketToken.OpenBracketToken
import com.alsaril.scheme.tokenizer.Tokenizer.tokenize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class TokenizerTest {
    @ParameterizedTest
    @MethodSource("simple", "negative numbers", "spaces", "unary", "symbols", "brackets")
    fun `splits source into tokens`(input: String, expected: List<Token>) {
        assertThat(tokenize(input)).containsExactlyElementsOf(expected)
    }

    @Test
    fun `rejects integers out of range`() {
        // when
        val exception = assertThrows<SchemeSyntaxException> { tokenize("(+ 99999999999 1)") }

        // then
        assertThat(exception.message).isEqualTo("integer 99999999999 is out of range")
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
            arguments(" 4)'.", listOf(ConstantToken(4), CloseBracketToken, QuoteToken, DotToken)),
            arguments(" 4+)'.", listOf(SymbolToken("4+"), CloseBracketToken, QuoteToken, DotToken)),
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
                "+1 -  -2 ++3 + 3",
                listOf(ConstantToken(1), SymbolToken("-"), ConstantToken(-2), SymbolToken("++3"), SymbolToken("+"), ConstantToken(3))
            ),
        )

        @JvmStatic
        fun symbols() = listOf(
            arguments("foo bar zog-zog?", listOf(SymbolToken("foo"), SymbolToken("bar"), SymbolToken("zog-zog?"))),
            arguments("baz-15 foo+15", listOf(SymbolToken("baz-15"), SymbolToken("foo+15"))),
            arguments(
                "add+one a+b 1+ 1abc -5x +-1",
                listOf("add+one", "a+b", "1+", "1abc", "-5x", "+-1").map(::SymbolToken)
            ),
            arguments("12'a 3(4)", listOf(
                ConstantToken(12), QuoteToken, SymbolToken("a"),
                ConstantToken(3), OpenBracketToken, ConstantToken(4), CloseBracketToken
            )),
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
                    SymbolToken("-25##-15"),
                    CloseBracketToken,
                    QuoteToken,
                    ConstantToken(-8)
                )
            ),
        )
    }
}
