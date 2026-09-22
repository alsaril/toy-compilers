package com.alsaril.scheme.parser

import com.alsaril.scheme.tokenizer.*
import com.alsaril.scheme.tokenizer.BracketToken.CloseBracketToken
import com.alsaril.scheme.tokenizer.BracketToken.OpenBracketToken

object Parser {

    fun parse(tokens: List<Token>): Node {
        val (node, next) = _parse(tokens, 0)
        require(next == tokens.size) { "unexpected trailing token ${tokens[next]} at index $next" }
        return node
    }

    private fun _parse(tokens: List<Token>, start: Int): Pair<Node, Int> {
        require(start < tokens.size) { "unexpected end of input" }

        fun processList(): Pair<Node, Int> {
            var i = start + 1
            val result = mutableListOf<Node>()
            var special = false
            while (i < tokens.size && tokens[i] != CloseBracketToken) {
                if (tokens[i] == DotToken) {
                    if (result.isEmpty()) {
                        throw IllegalArgumentException("unexpected token $DotToken at index $i")
                    }

                    val left = result.removeLast()
                    val (right, next) = _parse(tokens, i + 1)
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
                    val (node, next) = _parse(tokens, i)
                    result.add(node)
                    i = next
                }
            }
            if (i == tokens.size) {
                throw IllegalArgumentException("unexpected end of input, expected ')' to close list opened at index $start")
            }
            i++

            val tail = if (special) result.removeLast() else Null
            return result.foldRight(tail) { r, n -> Cell(r, n) } to i
        }

        return when (val token = tokens[start]) {
            is ConstantToken -> Number(token.value) to start + 1
            is SymbolToken -> Symbol(token.name) to start + 1
            is BracketToken -> if (token == OpenBracketToken) {
                processList()
            } else throw IllegalArgumentException("unexpected ')' at index $start")
            else -> throw IllegalArgumentException("unexpected token $token at index $start")
        }
    }
}