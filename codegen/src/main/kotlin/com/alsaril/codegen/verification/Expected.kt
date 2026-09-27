package com.alsaril.codegen.verification

sealed interface Expected

data class OfType(val type: VerificationType) : Expected {
    override fun toString() = type.toString()
}

data object AnyReference : Expected

data class OneOf(val types: List<ReferenceType>) : Expected {
    constructor(vararg types: ReferenceType) : this(types.toList())

    override fun toString() = "one of ${types.joinToString { it.descriptor }}"
}
