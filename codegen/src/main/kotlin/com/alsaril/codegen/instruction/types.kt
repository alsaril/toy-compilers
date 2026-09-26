package com.alsaril.codegen.instruction

sealed interface VerificationType {
    val slots: Int
}

enum class PrimitiveType(override val slots: Int = 1) : VerificationType {
    TOP(1),
    INTEGER(1),
    FLOAT(1),
    DOUBLE(2),
    LONG(2),
    NULL(1),
    UNINITIALIZED_THIS(1),
    VOID(0);
}

data class ReferenceType(val descriptor: String? = null, val descriptorIndex: Int? = null) : VerificationType {
    override val slots = 1

    init {
        require(descriptor != null && descriptorIndex == null || descriptor == null && descriptorIndex != null)
    }
}

data class Uninitialized(val offset: Int) : VerificationType {
    override val slots = 1
}

data object AnyReference : VerificationType {
    override val slots = 1
}
