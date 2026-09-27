package com.alsaril.codegen.verification

sealed interface VerificationType {
    val slots: Int
    val isAssignableToReference: Boolean
}

enum class PrimitiveType(override val slots: Int = 1, override val isAssignableToReference: Boolean = false) : VerificationType {
    TOP(1),
    INTEGER(1),
    FLOAT(1),
    DOUBLE(2),
    LONG(2),
    NULL(1, isAssignableToReference = true),
    UNINITIALIZED_THIS(1),
    VOID(0);
}

data class ReferenceType(val descriptor: String) : VerificationType {
    override val slots = 1
    override val isAssignableToReference = true
}

data class Uninitialized(val offset: Int) : VerificationType {
    override val slots = 1
    override val isAssignableToReference = false
}

data object AnyReference : VerificationType {
    override val slots = 1
    override val isAssignableToReference = true
}
