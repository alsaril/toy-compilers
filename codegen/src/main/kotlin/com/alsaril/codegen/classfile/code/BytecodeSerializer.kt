package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.DosWriter
import com.alsaril.codegen.classfile.Fragment
import com.alsaril.codegen.classfile.attributes.CodeAttribute
import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.classfile.attributes.StackMapFrame
import com.alsaril.codegen.classfile.attributes.StackMapTableAttribute
import com.alsaril.codegen.classfile.code.instruction.*
import com.alsaril.codegen.write
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.*
import kotlin.math.max

object BytecodeSerializer {
    fun serialize(fragment: Fragment, headerLocals: Int, cpEntry: (String) -> Int): CodeAttribute {
        val (maxStack, maxLocals) = analyze(fragment.instructions, fragment.jumps, fragment.exceptionHandlers)
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

    private fun analyze(instructions: List<Instruction>, jumps: Map<Int, Int>, exceptionHandlers: List<ExceptionHandler>): Pair<Int, Int> {
        val stackSize = IntArray(instructions.size) { -1 }
        var maxLocals = 0
        val deque = ArrayDeque<Pair<Int, Int>>()

        deque.addLast(0 to 0) // entry
        exceptionHandlers.forEach { deque.addLast(it.handlerPc to 1) } // handlers

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
            instruction.locals()?.let { maxLocals = max(maxLocals, it + 1) }

            nextPcs.forEach {
                if (it !in stackSize.indices) {
                    throw IllegalStateException()
                }
                deque.addLast(it to nextStack)
            }
        }

        stackSize.forEachIndexed { pc, depth -> require(depth != -1) { "instruction $pc is unreachable" } }
        val maxStack = stackSize.max()
        return maxStack to maxLocals
    }

    private fun nextStack(enterStack: Int, instruction: Instruction): Int {
        val (popCnt, pushCnt) = instruction.stackEffects()
        require(enterStack >= popCnt) { "$instruction pops $popCnt from a stack $enterStack deep" }
        return enterStack - popCnt + pushCnt
    }

    private fun nextPcs(instruction: Instruction, pc: Int, jumps: Map<Int, Int>): List<Int> {
        val dest = jumps[pc]
        return when (instruction) {
            IfEq, IfNe, IfGe, IfGt, IfICmpLt, IfICmpGe, IfNull, IfNotNull -> listOf(pc + 1, dest!!)
            Goto -> listOf(dest!!)
            Return, IReturn, FReturn, AThrow -> emptyList()
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

        var prev = -1
        val frames = fragment.frames
            .groupBy { frame -> i2loc[frame.offsetDelta]!! }
            .toSortedMap()
            .map { (loc, atLoc) ->
                val frame = atLoc.first()
                require(atLoc.all { it == frame }) {
                    "frames at offset $loc disagree, so the offset cannot be named once: ${atLoc.distinct()}"
                }
                patchOffset(frame, loc - prev - 1).also { prev = loc }
            }

        val exceptionHandlers = fragment.exceptionHandlers.map {
            it.copy(startPc = i2loc[it.startPc]!!, endPc = i2loc[it.endPc]!!, handlerPc = i2loc[it.handlerPc]!!)
        }

        return Triple(code, frames, exceptionHandlers)
    }

    internal fun ByteArray.s2At(pos: Int, value: Int) {
        require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "$value does not fit an s2" }
        this[pos] = (value shr 8).toByte()
        this[pos + 1] = value.toByte()
    }
}