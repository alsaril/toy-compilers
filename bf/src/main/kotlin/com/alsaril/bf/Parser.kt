package com.alsaril.bf

import com.alsaril.bf.Command.DEC
import com.alsaril.bf.Command.IN
import com.alsaril.bf.Command.INC
import com.alsaril.bf.Command.LEFT
import com.alsaril.bf.Command.OUT
import com.alsaril.bf.Command.RIGHT

object Parser {
    fun parse(input: String): List<Instruction> {
        val stack = mutableListOf<MutableList<Instruction>>()
        stack.add(mutableListOf())
        input.forEachIndexed { i, c ->
            val curr = stack.last()
            val prev = curr.lastOrNull()

            if (c == '[') {
                stack.add(mutableListOf())
                return@forEachIndexed
            } else if (c == ']') {
                val loop = Loop(stack.removeLast())
                if (stack.isEmpty()) {
                    throw IllegalArgumentException("unexpected ']' at $i")
                }
                stack.last().add(loop)
                return@forEachIndexed
            }

            val newCommand = when (c) {
                '<' -> LEFT
                '>' -> RIGHT
                '+' -> INC
                '-' -> DEC
                '.' -> OUT
                ',' -> IN
                else -> return@forEachIndexed
            }

            if (prev == null ||
                prev !is CommandInstruction ||
                prev.command == OUT ||
                prev.command == IN ||
                prev.command != newCommand
            ) {
                curr.add(CommandInstruction(newCommand))
            } else {
                curr.removeLast(); curr.add(prev.copy(times = prev.times + 1))
            }
        }

        if (stack.size > 1) {
            throw IllegalArgumentException("']' expected at ${input.length}")
        }
        return stack.removeLast()
    }
}