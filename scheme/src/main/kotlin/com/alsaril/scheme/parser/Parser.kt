package com.alsaril.scheme.parser

import com.alsaril.scheme.tokenizer.ConstantToken
import com.alsaril.scheme.tokenizer.Token

object Parser {
    fun parse(tokens: List<Token>): Node {
        val result = mutableListOf<Node>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token is ConstantToken) {
                result.add(Number(token.value))
                i++
                continue
            }

            TODO()
        }
        return result.first()
    }
}