package com.alsaril.scheme.parser

sealed interface Node

data class Number(val value: Int): Node

data class Symbol(val name: String): Node

data class Special(val name: String): Node

data class Cell(val first: Node, val second: Node): Node

data object Null: Node

/** The node written back out as source, for error messages. */
fun Node.source(): String = when (this) {
    is Number -> value.toString()
    is Symbol -> name
    is Special -> name
    Null -> "()"
    is Cell -> buildString {
        append('(').append(first.source())
        var rest = second
        while (rest is Cell) {
            append(' ').append(rest.first.source())
            rest = rest.second
        }
        if (rest != Null) append(" . ").append(rest.source())
        append(')')
    }
}
