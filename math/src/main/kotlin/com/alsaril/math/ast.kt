package com.alsaril.math

enum class BinaryKind {
    ADD, SUB, MUL, DIV;
}

sealed interface Node

data class Value(val value: Float) : Node
data class Var(val name: String) : Node
data class Op(val kind: BinaryKind, val left: Node, val right: Node) : Node