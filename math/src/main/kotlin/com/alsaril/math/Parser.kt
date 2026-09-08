package com.alsaril.math

import com.alsaril.math.BinaryKind.*
import com.alsaril.math.Parser.BinaryOperator.Type.*
import kotlin.math.exp

object Parser {
    private sealed interface Token

    private class BinaryOperator(val type: Type) : Token {
        enum class Type(val priority: Int) {
            PLUS(0), MINIS(0), TIMES(1), SLASH(1)
        }
    }

    private object LeftBracket : Token

    fun parse(expr: String): Node {
        var index = 0
        val resultStack = mutableListOf<Node>()
        val operatorsStack = mutableListOf<Token>()
        while (index != expr.length) {

            if (expr[index].isDigit()) {
                val start = index
                var dot = false
                while (index < expr.length && (expr[index].isDigit() || expr[index] == '.' && !dot)) {
                    if (!dot && expr[index] == '.') {
                        dot = true
                    }
                    ++index
                }
                resultStack.add(Value(expr.substring(start, index).toFloat()))
                continue
            }

            if (expr[index].isWhitespace()) {
                ++index
                continue
            }

            if (expr[index].isLetter()) {
                val sb = StringBuilder()
                while (index < expr.length && expr[index].isLetter()) {
                    sb.append(expr[index])
                    ++index
                }
                resultStack.add(Var(sb.toString()))
                continue
            }

            if (expr[index].isOperator()) {
                val type = when (expr[index]) {
                    '+' -> PLUS
                    '-' -> MINIS
                    '*' -> TIMES
                    '/' -> SLASH
                    else -> throw IllegalStateException("This shouldn't happen")
                }
                ++index
                reduceToPriority(resultStack, operatorsStack, type.priority)
                operatorsStack.add(BinaryOperator(type))
                continue
            }

            if (expr[index] == '(') {
                ++index
                operatorsStack.add(LeftBracket)
                continue
            }

            if (expr[index] == ')') {
                ++index
                reduceToBracket(resultStack, operatorsStack)
                continue
            }

            throw IllegalArgumentException("Unexpected symbol: ${expr[index]}")
        }

        while (operatorsStack.isNotEmpty()) {
            reduce(resultStack, operatorsStack)
        }

        if (resultStack.size != 1) {
            throw IllegalArgumentException("Malformed expression")
        }

        return resultStack.first()
    }

    private fun reduceToPriority(resultStack: MutableList<Node>, operatorsStack: MutableList<Token>, priority: Int) {
        if (operatorsStack.isEmpty()) {
            return
        }

        while (operatorsStack.isNotEmpty()) {
            val top = operatorsStack.last()
            if (top !is BinaryOperator || top.type.priority < priority) {
                break
            }
            reduce(resultStack, operatorsStack)
        }
    }

    private fun reduceToBracket(resultStack: MutableList<Node>, operatorsStack: MutableList<Token>) {
        if (operatorsStack.isEmpty()) {
            throw IllegalArgumentException("Left bracket isn't found")
        }

        while (operatorsStack.isNotEmpty() && operatorsStack.last() is BinaryOperator) {
            reduce(resultStack, operatorsStack)
        }

        if (operatorsStack.isEmpty() || operatorsStack.last() !is LeftBracket) {
            throw IllegalArgumentException("Left bracket isn't found")
        }

        operatorsStack.removeLast()
    }

    private fun reduce(resultStack: MutableList<Node>, operatorsStack: MutableList<Token>) {
        val right = resultStack.removeLast()
        val left = resultStack.removeLast()
        val op = operatorsStack.removeLast()

        require(op is BinaryOperator)
        val result = when (op.type) {
            PLUS -> Op(ADD, left, right)
            MINIS -> Op(SUB, left, right)
            TIMES -> Op(MUL, left, right)
            SLASH -> Op(DIV, left, right)
        }
        resultStack.add(result)
    }

    private fun Char.isOperator() = this == '+' || this == '-' || this == '*' || this == '/'
}