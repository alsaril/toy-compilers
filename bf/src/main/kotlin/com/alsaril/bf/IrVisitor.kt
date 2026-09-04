package com.alsaril.bf

import org.antlr.v4.runtime.tree.TerminalNode

sealed interface IrInstruction

enum class Command {
    LEFT, RIGHT, INC, DEC, OUT, IN;
}

data class CommandInstruction(val command: Command, val times: Int = 1) : IrInstruction

class JumpInstruction(
    val forward: Boolean,
) : IrInstruction {
    var destinationIndex: Int = -1
}

class IrVisitor : BFBaseVisitor<Unit>() {
    private val instructions = mutableListOf<IrInstruction>()

    override fun visitValue(ctx: BFParser.ValueContext) {
        val prev = instructions.lastOrNull()

        val type = (ctx.getChild(0) as TerminalNode).symbol.type
        val newCommand = when (type) {
            BFParser.LEFT -> Command.LEFT
            BFParser.RIGHT -> Command.RIGHT
            BFParser.INC -> Command.INC
            BFParser.DEC -> Command.DEC
            BFParser.OUT -> Command.OUT
            BFParser.IN -> Command.IN
            else -> throw IllegalArgumentException()
        }

        if (prev == null ||
            prev !is CommandInstruction ||
            prev.command == Command.OUT ||
            prev.command == Command.IN ||
            prev.command != newCommand
        ) {
            instructions.add(CommandInstruction(newCommand))
            return
        }

        instructions.removeLast()
        instructions.add(prev.copy(times = prev.times + 1))
    }

    override fun visitBlock(ctx: BFParser.BlockContext) {
        val begin = JumpInstruction(forward = true)
        instructions.add(begin)
        val beginIndex = instructions.lastIndex

        ctx.children.forEach(::visit)

        val end = JumpInstruction(forward = false)
        instructions.add(end)
        val endIndex = instructions.lastIndex

        begin.destinationIndex = endIndex
        end.destinationIndex = beginIndex
    }

    fun instructions(): List<IrInstruction> = instructions
}