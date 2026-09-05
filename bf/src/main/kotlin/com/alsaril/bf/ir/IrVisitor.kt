package com.alsaril.bf.ir

import com.alsaril.bf.BFBaseVisitor
import com.alsaril.bf.BFParser
import org.antlr.v4.runtime.tree.TerminalNode


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
            prev.command != newCommand ||
            prev.times == Short.MAX_VALUE.toInt()
        ) {
            instructions.add(CommandInstruction(newCommand))
            return
        }

        instructions.removeLast()
        instructions.add(prev.copy(times = prev.times + 1))
    }

    override fun visitBlock(ctx: BFParser.BlockContext) {
        instructions.add(LoopBegin)
        val beginIndex = instructions.lastIndex

        ctx.children.forEach(::visit)

        val end = LoopEnd(beginIndex)
        instructions.add(end)
    }

    fun instructions(): List<IrInstruction> = instructions
}