package com.alsaril.codegen.instruction

import com.alsaril.codegen.verification.Expected
import com.alsaril.codegen.verification.OfType
import com.alsaril.codegen.verification.VerificationType

sealed interface StackEffect

data class Pop(val expected: Expected) : StackEffect {
    constructor(type: VerificationType) : this(OfType(type))
}

data class Push(val type: VerificationType) : StackEffect

sealed interface LocalEffect {
    val index: Int
    val type: VerificationType
}

data class Read(override val index: Int, override val type: VerificationType): LocalEffect
data class Write(override val index: Int, override val type: VerificationType): LocalEffect

sealed interface Invocation : Instruction {
    val args: List<VerificationType>
    val returnType: VerificationType

    override fun stackEffects(): List<StackEffect> = args.map { Pop(it) } + Push(returnType)
}
