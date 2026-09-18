package com.alsaril.scheme.parser

sealed interface Node

data class Number(val value: Int): Node

data class Symbol(val name: String): Node

data class Cell(val first: Node, val second: Node): Node

data object Null: Node