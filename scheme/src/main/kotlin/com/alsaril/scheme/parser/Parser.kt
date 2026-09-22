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

    private fun _parse(tokens: List<Token>, start: Int): Triple<Node, Int, Boolean> {
        require(start < tokens.size) { "unexpected end of input" }

        fun processList(): Pair<Node, Int> {
            var i = start + 1
            val result = mutableListOf<Node>()
            var special = false
            while (i < tokens.size && tokens[i] != CloseBracketToken) {
                val (node, next, spec) = _parse(tokens, i)
                i = next
                result.add(node)
                if (spec) {
                    if (tokens[i] != CloseBracketToken) {
                        // sanity check but should be true always
                        throw IllegalArgumentException("expected ')' after dotted pair tail at index $i")
                    }
                    special = true
                }
            }
            if (i == tokens.size) {
                throw IllegalArgumentException("unexpected end of input, expected ')' to close list opened at index $start")
            }
            i++

            if (!special) {
                return result.foldRight(Null as Node) { r, n -> Cell(r, n) } to i
            }

            return result.asReversed().asSequence().drop(1).fold(result.last()){r, n -> Cell(n, r) } to i
        }

        val (candidate, next) = when (val token = tokens[start]) {
            is ConstantToken -> Number(token.value) to start + 1
            is SymbolToken -> Symbol(token.name) to start + 1
            is BracketToken -> if (token == OpenBracketToken) {
                processList()
            } else throw IllegalArgumentException("unexpected ')' at index $start")
            else -> throw IllegalArgumentException("unexpected token $token at index $start")
        }

        if (next >= tokens.size || tokens[next] != DotToken) {
            return Triple(candidate, next, false)
        }

        val (second, sNext) = _parse(tokens, next + 1)
        if (sNext < tokens.size && tokens[sNext] == CloseBracketToken) {
            return Triple(Cell(candidate, second), sNext, true)
        } else {
            throw IllegalArgumentException("expected ')' after dotted pair tail at index $sNext")
        }
    }
}