package com.alsaril.codegen.instruction

import com.alsaril.codegen.verification.VerificationType

sealed interface StackEffect {
    val type: VerificationType
}

data class Pop(override val type: VerificationType) : StackEffect
data class Push(override val type: VerificationType) : StackEffect

sealed interface LocalEffect {
    val index: Int
    val type: VerificationType
}

data class Read(override val index: Int, override val type: VerificationType): LocalEffect
data class Write(override val index: Int, override val type: VerificationType): LocalEffect
