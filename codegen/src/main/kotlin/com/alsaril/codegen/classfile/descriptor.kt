package com.alsaril.codegen.classfile

import com.alsaril.codegen.classfile.PrimitiveType.Companion.asPrimitiveType
import com.alsaril.codegen.classfile.PrimitiveType.VOID
import com.alsaril.codegen.verification.VerificationType

sealed interface Type {
    val descriptor: String
    val verificationType: VerificationType
}

enum class PrimitiveType(override val descriptor: String, override val verificationType: VerificationType) : Type {
    BYTE("B", com.alsaril.codegen.verification.PrimitiveType.INTEGER),
    CHAR("C", com.alsaril.codegen.verification.PrimitiveType.INTEGER),
    DOUBLE("D", com.alsaril.codegen.verification.PrimitiveType.DOUBLE),
    FLOAT("F", com.alsaril.codegen.verification.PrimitiveType.FLOAT),
    INTEGER("I", com.alsaril.codegen.verification.PrimitiveType.INTEGER),
    LONG("J", com.alsaril.codegen.verification.PrimitiveType.LONG),
    SHORT("S", com.alsaril.codegen.verification.PrimitiveType.INTEGER),
    BOOLEAN("Z", com.alsaril.codegen.verification.PrimitiveType.INTEGER),
    VOID("V", com.alsaril.codegen.verification.PrimitiveType.VOID);

    companion object {
        private val c2type = entries.associateBy { it.descriptor }

        fun Char.asPrimitiveType() = c2type[this.toString()]
    }
}

data class ArrayType(val elem: Type) : Type {
    override val descriptor = "[${elem.descriptor}"
    override val verificationType = com.alsaril.codegen.verification.ReferenceType(descriptor)
}

data class ReferenceType(val clazz: String) : Type {
    override val descriptor = "L$clazz;"
    override val verificationType = com.alsaril.codegen.verification.ReferenceType(clazz)
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

    symbol.asPrimitiveType()?.let { return it to pos + 1 }

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