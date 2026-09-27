package com.alsaril.codegen.code

import com.alsaril.codegen.instruction.Instruction
import com.alsaril.codegen.verification.PrimitiveType.TOP
import com.alsaril.codegen.verification.VerificationType

internal data class Locals(private val slots: List<VerificationType>) {
    init {
        slots.forEachIndexed { index, type ->
            check(type.slots < 2 || slots.getOrNull(index + 1) == TOP) {
                "$type in local $index is missing its second half: $slots"
            }
        }
    }

    val size get() = slots.size

    fun read(index: Int, instruction: Instruction, pc: Int): VerificationType {
        require(index < size) { "$instruction at $pc reads local $index, but only $size local slots are defined" }
        return slots[index]
    }

    fun write(index: Int, type: VerificationType): Locals {
        val slots = slots.toMutableList()
        while (slots.size < index + type.slots) slots.add(TOP)
        if (index > 0 && slots[index - 1].slots == 2) slots[index - 1] = TOP
        slots[index] = type
        if (type.slots == 2) slots[index + 1] = TOP
        return Locals(slots)
    }

    fun merge(other: Locals, merge: (VerificationType, VerificationType) -> VerificationType?) =
        Locals((slots zip other.slots).map { (a, b) -> merge(a, b) ?: TOP })

    fun initialise(from: VerificationType, to: VerificationType) = Locals(slots.map { if (it == from) to else it })

    fun <T> entries(transform: (VerificationType) -> T): List<T> {
        val entries = mutableListOf<T>()
        var index = 0
        while (index < size) {
            entries.add(transform(slots[index]))
            index += slots[index].slots
        }
        return entries
    }

    companion object {
        fun of(types: List<VerificationType>) = types.fold(Locals(emptyList())) { locals, type ->
            locals.write(locals.size, type)
        }
    }
}
