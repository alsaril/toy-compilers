package com.alsaril.codegen.instruction

sealed interface StackEffect {
    val type: VerificationType
}

data class Pop(override val type: VerificationType) : StackEffect
data class Push(override val type: VerificationType) : StackEffect

internal interface TouchesLocal : Instruction {
    val index: Int

    val slots: Int get() = 1

    override fun locals() = index + slots
}
