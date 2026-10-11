package com.alsaril.scheme.analyser

import com.alsaril.scheme.analyser.ResolvedReference.Location.*
import com.alsaril.scheme.parser.Node

sealed interface Expression

enum class BooleanConstant : Expression {
    FALSE, TRUE;
}

data class NumberConstant(val value: Int) : Expression

data class Reference(val name: String): Expression

class Call(val function: Expression, val args: List<Expression>, val tail: Boolean = false) : Expression

class BooleanExpression(val identity: Boolean, val args: List<Expression>, val tail: Boolean = false) : Expression

class Datum(val value: Node) : Expression

class IfExpression(
    val condition: Expression,
    val thenBranch: Expression,
    val elseBranch: Expression?,
    val tail: Boolean = false
) : Expression

class Bind(val type: Type, val reference: Reference, val value: Expression) : Expression {
    enum class Type { DEFINE, SET }
}

data class ResolvedReference(
    val name: String,
    val location: Location,
    val boxed: Boolean,
    val index: Int, // local indexes -- walkthrough
) {
    enum class Location { GLOBAL, CAPTURE, ARGUMENT, LOCAL }
}

class Frame(val references: List<ResolvedReference>) {
    private val map = references.associateBy { it.name }

    val globals by lazy { references.filter { it.location == GLOBAL } }
    val captures by lazy { references.filter { it.location == CAPTURE } }
    val arguments by lazy { references.filter { it.location == ARGUMENT } }
    val locals by lazy { references.filter { it.location == LOCAL } }

    fun resolve(name: String) = map[name]!!
}

class Lambda(val args: List<String>, val body: List<Expression>, val frame: Frame) : Expression

class GlobalForm(val body: Expression, val frame: Frame) : Expression

