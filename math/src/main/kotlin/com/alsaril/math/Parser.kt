package com.alsaril.math

import com.alsaril.math.BinaryKind.*
import com.alsaril.math.Parser.BinaryOperator.Type.*

object Parser {
    private sealed interface Token

    private class BinaryOperator(val type: Type) : Token {
        enum class Type(val priority: Int) {
            PLUS(0), MINUS(0), TIMES(1), SLASH(1)
        }
    }

    private object LeftBracket : Token

    fun parse(expr: String): Node {
        var index = 0
        val resultStack = mutableListOf<Node>()
        val operatorsStack = mutableListOf<Token>()

        var operandExpected = true

        while (index != expr.length) {
            val symbol = expr[index]

            if (symbol.isWhitespace()) {
                ++index
                continue
            }

            if (symbol.isDigit()) {
                require(operandExpected) { "operator expected at $index" }
                val start = index
                var dot = false
                while (index < expr.length && (expr[index].isDigit() || expr[index] == '.' && !dot)) {
                    if (expr[index] == '.') {
                        dot = true
                    }
                    ++index
                }
                resultStack.add(Value(expr.substring(start, index).toFloat()))
                operandExpected = false
                continue
            }

            if (symbol.isLetter()) {
                require(operandExpected) { "operator expected at $index" }
                val start = index
                while (index < expr.length && expr[index].isLetter()) {
                    ++index
                }
                resultStack.add(Var(expr.substring(start, index)))
                operandExpected = false
                continue
            }

            val operator = symbol.operator()
            if (operator != null) {
                require(!operandExpected) { "operand expected at $index" }
                ++index
                reduceToPriority(resultStack, operatorsStack, operator.priority)
                operatorsStack.add(BinaryOperator(operator))
                operandExpected = true
                continue
            }

            if (symbol == '(') {
                require(operandExpected) { "operator expected at $index" }
                ++index
                operatorsStack.add(LeftBracket)
                continue
            }

            if (symbol == ')') {
                require(!operandExpected) { "operand expected at $index" }
                reduceToBracket(resultStack, operatorsStack, index)
                ++index
                continue
            }

            throw IllegalArgumentException("unexpected '$symbol' at $index")
        }

        require(!operandExpected) { "operand expected at ${expr.length}" }

        while (operatorsStack.isNotEmpty()) {
            require(operatorsStack.last() !is LeftBracket) { "')' expected at ${expr.length}" }
            reduce(resultStack, operatorsStack)
        }


        return resultStack.single()
    }

    private fun reduceToPriority(resultStack: MutableList<Node>, operatorsStack: MutableList<Token>, priority: Int) {
        while (operatorsStack.isNotEmpty()) {
            val top = operatorsStack.last()
            if (top !is BinaryOperator || top.type.priority < priority) {
                break
            }
            reduce(resultStack, operatorsStack)
        }
    }

    private fun reduceToBracket(resultStack: MutableList<Node>, operatorsStack: MutableList<Token>, index: Int) {
        while (operatorsStack.isNotEmpty() && operatorsStack.last() is BinaryOperator) {
            reduce(resultStack, operatorsStack)
        }


        require(operatorsStack.isNotEmpty()) { "unexpected ')' at $index" }
        operatorsStack.removeLast()
    }

    private fun reduce(resultStack: MutableList<Node>, operatorsStack: MutableList<Token>) {
        val right = resultStack.removeLast()
        val left = resultStack.removeLast()
        val op = operatorsStack.removeLast() as BinaryOperator

        val result = when (op.type) {
            PLUS -> Op(ADD, left, right)
            MINUS -> Op(SUB, left, right)
            TIMES -> Op(MUL, left, right)
            SLASH -> Op(DIV, left, right)
        }
        resultStack.add(result)
    }

    private fun Char.operator() = when (this) {
        '+' -> PLUS
        '-' -> MINUS
        '*' -> TIMES
        '/' -> SLASH
        else -> null
    }
}
