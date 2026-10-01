package com.alsaril.scheme.tokenizer

import com.alsaril.scheme.SchemeSyntaxException
import com.alsaril.scheme.tokenizer.BracketToken.CloseBracketToken
import com.alsaril.scheme.tokenizer.BracketToken.OpenBracketToken

object Tokenizer {
    private val integer = Regex("[+-]?[0-9]+")

    private fun parseSpecial(symbol: Char) = when (symbol) {
        '(' -> OpenBracketToken
        ')' -> CloseBracketToken
        '.' -> DotToken
        '\'' -> QuoteToken
        else -> null
    }

    private fun isDelimiter(symbol: Char) = symbol.isWhitespace() || parseSpecial(symbol) != null

    // a run of characters is a number if it is one as a whole, and a symbol otherwise
    private fun atom(text: String): Token {
        if (!integer.matches(text)) return SymbolToken(text)
        val value = text.toIntOrNull() ?: throw SchemeSyntaxException("integer $text is out of range")
        return ConstantToken(value)
    }

    fun tokenize(str: String): List<Token> {
        val result = mutableListOf<Token>()

        var i = 0
        while (i < str.length) {
            val symbol = str[i]
            if (symbol.isWhitespace()) {
                i++
                continue
            }

            parseSpecial(symbol)?.let {
                result.add(it)
                i++
                continue
            }

            val start = i
            while (i < str.length && !isDelimiter(str[i])) i++
            result.add(atom(str.substring(start, i)))
        }

        return result
    }
}
