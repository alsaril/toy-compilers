package com.alsaril.codegen.instruction

import com.alsaril.codegen.verification.Expected
import com.alsaril.codegen.verification.OfType
import com.alsaril.codegen.verification.PrimitiveType.VOID
import com.alsaril.codegen.verification.VerificationType

data class StackEffect(val before: List<Expected>, val after: List<VerificationType>) {
    companion object {
        val NONE = StackEffect(emptyList(), emptyList())
    }
}

fun takes(vararg before: VerificationType) = StackEffect(before.map(::OfType), emptyList())

fun takes(vararg before: Expected) = StackEffect(before.toList(), emptyList())

fun gives(vararg after: VerificationType) = StackEffect(emptyList(), after.toList())

infix fun StackEffect.gives(type: VerificationType) = copy(after = listOf(type))

sealed interface LocalEffect {
    val index: Int
    val type: VerificationType
}

data class Read(override val index: Int, override val type: VerificationType): LocalEffect
data class Write(override val index: Int, override val type: VerificationType): LocalEffect

sealed interface Invocation : Instruction {
    val args: List<VerificationType>
    val returnType: VerificationType

    override fun stackEffect() = StackEffect(args.map(::OfType), listOfNotNull(returnType.takeIf { it != VOID }))
}
