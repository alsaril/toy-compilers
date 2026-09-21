package com.alsaril.codegen.code

import com.alsaril.codegen.DosWriter
import com.alsaril.codegen.classfile.attributes.CodeAttribute
import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.classfile.attributes.StackMapFrame
import com.alsaril.codegen.classfile.attributes.StackMapTableAttribute
import com.alsaril.codegen.instruction.*
import com.alsaril.codegen.write
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.*
import kotlin.math.max

object BytecodeSerializer {
    fun serialize(fragment: Fragment, headerLocals: Int, cpEntry: (String) -> Int): CodeAttribute {
        val (maxStack, maxLocals) = analyze(fragment)
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

    private fun analyze(fragment: Fragment): Pair<Int, Int> {
        val instructions = fragment.instructions
        require(instructions.isNotEmpty()) { "a method body must hold at least one instruction" }

        val stackSize = IntArray(instructions.size) { -1 }
        var maxLocals = 0
        val deque = ArrayDeque<Pair<Int, Int>>()

        deque.addLast(0 to 0) // entry
        fragment.exceptionHandlers.forEach { // handlers, entered with the throwable alone
            require(it.handlerPc in stackSize.indices) {
                "a handler starts at ${it.handlerPc}, which is past the last instruction"
            }
            deque.addLast(it.handlerPc to 1)
        }

        while (deque.isNotEmpty()) {
            val (pc, enterStack) = deque.removeFirst()
            if (stackSize[pc] == -1) {
                stackSize[pc] = enterStack
            } else if (stackSize[pc] != enterStack) {
                throw IllegalStateException(
                    "${instructions[pc]} at $pc is reached with a stack ${stackSize[pc]} deep " +
                            "on one path and $enterStack deep on another"
                )
            } else continue

            val instruction = instructions[pc]

            val nextPcs: List<Int> = nextPcs(instruction, pc, fragment.jumps)
            val nextStack: Int = nextStack(enterStack, pc, instruction)
            instruction.locals()?.let { maxLocals = max(maxLocals, it) }

            nextPcs.forEach {
                if (it !in stackSize.indices) {
                    throw IllegalStateException(
                        "$instruction at $pc continues to $it, which is past the last instruction"
                    )
                }
                deque.addLast(it to nextStack)
            }
        }

        stackSize.forEachIndexed { pc, depth -> require(depth != -1) { "instruction $pc is unreachable" } }
        val maxStack = stackSize.max()
        return maxStack to maxLocals
    }

    private fun nextStack(enterStack: Int, pc: Int, instruction: Instruction): Int {
        val (popCnt, pushCnt) = instruction.stackEffects()
        require(enterStack >= popCnt) { "$instruction at $pc pops $popCnt from a stack $enterStack deep" }
        return enterStack - popCnt + pushCnt
    }

    private fun nextPcs(instruction: Instruction, pc: Int, jumps: Map<Int, Int>): List<Int> {
        fun target() = requireNotNull(jumps[pc]) { "$instruction at $pc was never linked to a target" }

        return when (instruction) {
            ifeq, ifne, ifge, ifgt, if_icmplt, if_icmpge, ifnull, ifnonnull -> listOf(pc + 1, target())
            goto -> listOf(target())
            `return`, ireturn, freturn, athrow -> emptyList()
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

        fun loc(index: Int, what: String) = requireNotNull(i2loc[index]) {
            "$what names instruction $index, which this fragment does not hold"
        }

        fragment.jumps.forEach { (from, to) ->
            val instruction = fragment.instructions[from]
            require(instruction is JumpInstruction) {
                "instruction $from is linked to $to, but $instruction is not a jump"
            }
            val fromLoc = loc(from, "a jump")
            code.s2At(fromLoc + 1, loc(to, "a jump target") - fromLoc)
        }

        var prev = -1
        val frames = fragment.frames
            .groupBy { frame -> loc(frame.offsetDelta, "a frame") }
            .toSortedMap()
            .map { (loc, atLoc) ->
                val frame = atLoc.first()
                require(atLoc.all { it == frame }) {
                    "frames at offset $loc disagree, so the offset cannot be named once: ${atLoc.distinct()}"
                }
                patchOffset(frame, loc - prev - 1).also { prev = loc }
            }

        val exceptionHandlers = fragment.exceptionHandlers.map {
            it.copy(
                startPc = loc(it.startPc, "a guarded range"),
                endPc = if (it.endPc == fragment.instructions.size) code.size
                else loc(it.endPc, "a guarded range"),
                handlerPc = loc(it.handlerPc, "a handler"),
            )
        }

        return Triple(code, frames, exceptionHandlers)
    }

    internal fun ByteArray.s2At(pos: Int, value: Int) {
        require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "$value does not fit an s2" }
        this[pos] = (value shr 8).toByte()
        this[pos + 1] = value.toByte()
    }
}