package com.alsaril.codegen.classfile

import com.alsaril.codegen.classfile.PrimitiveType.*

sealed interface Type {
    val slots: Int
}

enum class PrimitiveType(override val slots: Int = 1) : Type {
    BYTE, CHAR, DOUBLE(2), FLOAT, INT, LONG(2), SHORT, BOOLEAN, VOID(0)
}

data class ArrayType(val elem: Type) : Type {
    override val slots = 1
}

data class ReferenceType(val clazz: String) : Type {
    override val slots = 1
}

data class FunctionDescriptor(val args: List<Type>, val returnType: Type)

fun parseType(descriptor: String): Type {
    val (type, next) = parseNextType(descriptor, 0)
    require(next == descriptor.length) { "unexpected symbol at $next: ${descriptor[next]}" }
    return type
}

private fun parseNextType(descriptor: String, pos: Int): Pair<Type, Int> {
    require(pos < descriptor.length) { "type expected at $pos" }
    val symbol = descriptor[pos]

    if (symbol == '[') {
        val (type, next) = parseNextType(descriptor, pos + 1)
        return ArrayType(type) to next
    }

    if (symbol == 'L') {
        var index = pos + 1
        while (index < descriptor.length && descriptor[index] != ';') index++
        require(index != descriptor.length) { "';' expected at ${descriptor.length}" }
        return ReferenceType(descriptor.substring(pos + 1, index)) to index + 1
    }

    when (symbol) {
        'B' -> BYTE
        'C' -> CHAR
        'D' -> DOUBLE
        'F' -> FLOAT
        'I' -> INT
        'J' -> LONG
        'S' -> SHORT
        'Z' -> BOOLEAN
        'V' -> VOID
        else -> null
    }?.let { return it to pos + 1 }

    throw IllegalArgumentException("unknown symbol at $pos: $symbol")
}


fun parseFunctionDescriptor(descriptor: String): FunctionDescriptor {
    require(descriptor.isNotEmpty() && descriptor[0] == '(') { "'(' expected at 0" }

    var index = 1
    val args = mutableListOf<Type>()

    while (index < descriptor.length) {
        val symbol = descriptor[index]
        if (symbol == ')') {
            val (rType, next) = parseNextType(descriptor, index + 1)
            require(next == descriptor.length) { "unexpected symbol at $next: ${descriptor[next]}" }
            return FunctionDescriptor(args, rType)
        }
        val (type, next) = parseNextType(descriptor, index)
        require(type != VOID) { "void cannot be an argument at $index" }
        args.add(type)
        index = next
    }

    throw IllegalArgumentException("')' expected at ${descriptor.length}")
}