package com.alsaril.scheme.tokenizer

object Tokenizer {
    fun tokenize(str: String): List<Token> {
        val result = mutableListOf<Token>()

        var i = 0
        while (i < str.length) {
            val symbol = str[i]
            if (symbol == ' ') {
                i++
                continue
            }

            if (symbol.isDigit() || symbol == '-') {
                val start = i++
                while (i < str.length && str[i].isDigit()) i++
                result.add(Number(str.substring(start, i).toInt()))
                continue
            }

            throw IllegalArgumentException("unknown symbol at $i: $symbol")
        }

        return result
    }
}
