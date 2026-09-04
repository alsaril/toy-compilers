package com.alsaril.bf.ir

sealed interface IrInstruction

enum class Command {
    LEFT, RIGHT, INC, DEC, OUT, IN;
}

data class CommandInstruction(val command: Command, val times: Int = 1) : IrInstruction

data object LoopBegin: IrInstruction

data class LoopEnd(val startIndex: Int): IrInstruction