package com.alsaril.bf

sealed interface Instruction

enum class Command {
    LEFT, RIGHT, INC, DEC, OUT, IN;
}

data class CommandInstruction(val command: Command, val times: Int = 1) : Instruction

data class Loop(val instructions: List<Instruction>) : Instruction