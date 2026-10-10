package com.alsaril.scheme.analyser

import com.alsaril.scheme.analyser.Reference.Location.*
import com.alsaril.scheme.parser.Node

sealed interface Expression

enum class BooleanConstant : Expression {
    FALSE, TRUE;
}

data class NumberConstant(val value: Int) : Expression

data class Reference(
    val name: String,
    val location: Location,
    val boxed: Boolean,
    val index: Int,
) : Expression {
    enum class Location { GLOBAL, CAPTURE, LOCAL }

    fun fieldName() = when (location) {
        GLOBAL -> "g$index"
        CAPTURE -> "c$index"
        LOCAL -> throw IllegalStateException()
    }
}

class Call(val function: Expression, val args: List<Expression>, val tail: Boolean) : Expression

class BooleanExpression(val identity: Boolean, val args: List<Expression>, val tail: Boolean) : Expression

class Datum(val value: Node) : Expression

class IfExpression(
    val condition: Expression,
    val thenBranch: Expression,
    val elseBranch: Expression?,
    val tail: Boolean
) : Expression

class Bind(val type: Type, val reference: Reference, val value: Expression) : Expression {
    enum class Type { DEFINE, SET }
}

class Lambda(val args: List<String>, val body: List<Expression>, val references: List<Reference>) : Expression

class GlobalForm(val body: Expression, val references: List<Reference>) : Expression

