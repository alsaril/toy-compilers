package com.alsaril.scheme.parser

import com.alsaril.scheme.tokenizer.BracketToken.CloseBracketToken
import com.alsaril.scheme.tokenizer.BracketToken.OpenBracketToken
import com.alsaril.scheme.tokenizer.ConstantToken
import com.alsaril.scheme.tokenizer.DotToken
import com.alsaril.scheme.tokenizer.QuoteToken
import com.alsaril.scheme.tokenizer.SymbolToken
import com.alsaril.scheme.tokenizer.Token

object Parser {

    fun parse(tokens: List<Token>): Node {
        val (node, next) = parseNode(tokens, 0)
        require(next == tokens.size) { "unexpected trailing token ${tokens[next]} at index $next" }
        return node
    }

    private fun parseNode(tokens: List<Token>, start: Int): Pair<Node, Int> {
        require(start < tokens.size) { "unexpected end of input" }

        return when (val token = tokens[start]) {
            is ConstantToken -> Number(token.value) to start + 1
            is SymbolToken -> Symbol(token.name) to start + 1
            OpenBracketToken -> processList(tokens, start + 1)
            CloseBracketToken -> throw IllegalArgumentException("unexpected ')' at index $start")
            QuoteToken -> {
                val (arg, next) = parseNode(tokens, start + 1)
                Cell(Symbol("quote"), Cell(arg, Null)) to next
            }
            DotToken -> throw IllegalArgumentException("unexpected token $token at index $start")
        }
    }

    private fun processList(tokens: List<Token>, start: Int): Pair<Node, Int> {
        var i = start
        val result = mutableListOf<Node>()
        var special = false
        while (i < tokens.size && tokens[i] != CloseBracketToken) {
            if (tokens[i] == DotToken) {
                if (result.isEmpty()) {
                    throw IllegalArgumentException("unexpected token $DotToken at index $i")
                }

                val left = result.removeLast()
                val (right, next) = parseNode(tokens, i + 1)
                if (next >= tokens.size) {
                    throw IllegalArgumentException("unexpected end of input, expected ')' after dotted pair tail")
                }
                if (tokens[next] != CloseBracketToken) {
                    throw IllegalArgumentException("expected ')' after dotted pair tail at index $next")
                }
                result.add(Cell(left, right))
                i = next
                special = true
            } else {
                val (node, next) = parseNode(tokens, i)
                result.add(node)
                i = next
            }
        }
        if (i == tokens.size) {
            throw IllegalArgumentException("unexpected end of input, expected ')' to close list opened at index ${start - 1}")
        }
        i++

        val tail = if (special) result.removeLast() else Null
        return result.foldRight(tail) { r, n -> Cell(r, n) } to i
    }
}