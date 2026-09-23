package com.alsaril.scheme.tokenizer

import com.alsaril.scheme.tokenizer.BracketToken.CloseBracketToken
import com.alsaril.scheme.tokenizer.BracketToken.OpenBracketToken

object Tokenizer {
    private fun isStartOfNumber(str: String, pos: Int): Boolean {
        return str[pos].isDigit() || (pos < str.length - 1 && str[pos + 1].isDigit() && (str[pos] == '-' || str[pos] == '+'))
    }

    private fun parseSpecial(symbol: Char) = when (symbol) {
        '(' -> OpenBracketToken
        ')' -> CloseBracketToken
        '.' -> DotToken
        '\'' -> QuoteToken
        else -> null
    }

    fun tokenize(str: String): List<Token> {
        val result = mutableListOf<Token>()

        var i = 0
        while (i < str.length) {
            val symbol = str[i]
            if (symbol == ' ') {
                i++
                continue
            }

            if (isStartOfNumber(str, i)) {
                val start = i++
                while (i < str.length && str[i].isDigit()) i++
                result.add(ConstantToken(str.substring(start, i).toInt()))
                continue
            }

            parseSpecial(symbol)?.let {
                result.add(it)
                i++
                continue
            }

            if (symbol == '+') {
                result.add(SymbolToken("+"))
                i++
                continue
            }

            run { // symbols
                val start = i
                while (i < str.length && str[i] != ' ' && str[i] != '+' && parseSpecial(str[i]) == null) i++
                result.add(SymbolToken(str.substring(start, i)))
                continue
            }
        }

        return result
    }
}
