package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.DosWriter
import com.alsaril.codegen.classfile.Fragment
import com.alsaril.codegen.classfile.attributes.*
import com.alsaril.codegen.write
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.*
import kotlin.math.max

object BytecodeSerializer {
    fun serialize(fragment: Fragment, headerLocals: Int, cpEntry: (String) -> Int): CodeAttribute {
        val (maxStack, maxLocals) = analyze(fragment.instructions, fragment.jumps)
        val (bytecode, frames, exceptionHandlers) = emit(fragment)

        return CodeAttribute(
            cpEntry("Code"),
            maxStack,
            max(headerLocals, maxLocals),
            bytecode,
            exceptionHandlers,
            listOf(StackMapTableAttribute(cpEntry("StackMapTable"), frames)),
        )
    }

    private fun analyze(instructions: List<Instruction>, jumps: Map<Int, Int>): Pair<Int, Int> {
        val stackSize = IntArray(instructions.size) { -1 }
        var maxLocals = 0
        val deque = ArrayDeque<Pair<Int, Int>>()
        deque.addLast(0 to 0)

        while (deque.isNotEmpty()) {
            val (pc, enterStack) = deque.removeFirst()
            if (stackSize[pc] == -1) {
                stackSize[pc] = enterStack
            } else if (stackSize[pc] != enterStack) {
                throw IllegalStateException()
            } else continue

            val instruction = instructions[pc]

            val nextPcs: List<Int> = nextPcs(instruction, pc, jumps)
            val nextStack: Int = nextStack(enterStack, instruction)
            //maxLocals = max(maxLocals, locals(instruction))

            nextPcs.forEach {
                if (it !in stackSize.indices) {
                    throw IllegalStateException()
                }
                deque.addLast(it to nextStack)
            }
        }

        stackSize.forEach { require(it != -1) }
        val maxStack = stackSize.max()
        return maxStack to maxLocals
    }

    private fun nextStack(enterStack: Int, instruction: Instruction): Int {
        val (popCnt, pushCnt) = instruction.stackEffects()
        require(enterStack >= popCnt)
        return enterStack - popCnt + pushCnt
    }

    private fun nextPcs(instruction: Instruction, pc: Int, jumps: Map<Int, Int>): List<Int> {
        val dest = jumps[pc]
        return when (instruction) {
            IfEq, IfNe, IfGe, IfGt, IfICmpLt, IfICmpGe, Goto, IfNull, IfNotNull -> listOf(pc + 1, dest!!)
            else -> listOf(pc + 1)
        }
    }

    internal fun emit(fragment: Fragment): Triple<ByteArray, List<StackMapFrame>, List<ExceptionHandler>> {
        val output = ByteArrayOutputStream()
        val writer = DosWriter(DataOutputStream(output))
        val i2loc = mutableMapOf<Int, Int>()

        fragment.instructions.forEachIndexed { index, instruction ->
            i2loc[index] = output.size()
            writer.write(instruction)
        }

        val code = output.toByteArray()

        fragment.jumps.forEach { (from, to) ->
            val instruction = fragment.instructions[from]
            when (instruction) {
                IfEq, IfNe, IfGe, IfGt, IfICmpLt, IfICmpGe, Goto, IfNull, IfNotNull -> {
                    val fromLoc = i2loc[from]!!
                    val toLoc = i2loc[to]!!
                    code.s2At(fromLoc + 1, toLoc - fromLoc)
                }

                else -> throw IllegalArgumentException()
            }
        }

        // todo test several frames at one loc
        var prev = -1
        val frames = fragment.frames
            .asSequence()
            .map { (instruction, frame) -> i2loc[instruction]!! to frame }
            .sortedBy { it.first }
            .map { (loc, frame) ->
                when (frame) {
                    is AppendFrame -> frame.copy(offsetDelta = loc - prev - 1)
                    is FullFrame -> frame.copy(offsetDelta = loc - prev - 1)
                    is SameFrame, is SameFrameExtended -> sameFrame(offsetDelta = loc - prev - 1)
                    is SameLocals1StackItemFrame -> sameLocals1StackItem(offsetDelta = loc - prev - 1, frame.stack)
                }.also { prev = loc }
            }.toList()

        // handlers

        return Triple(code, frames, emptyList())
    }

    internal fun ByteArray.s2At(pos: Int, value: Int) {
        require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "$value does not fit an s2" }
        this[pos] = (value shr 8).toByte()
        this[pos + 1] = value.toByte()
    }
}