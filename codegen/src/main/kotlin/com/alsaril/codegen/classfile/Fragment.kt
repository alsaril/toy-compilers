package com.alsaril.codegen.classfile

import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.classfile.attributes.StackMapFrame
import com.alsaril.codegen.classfile.code.BytecodeSerializer
import com.alsaril.codegen.classfile.code.instruction.Instruction


data class Fragment(
    val instructions: List<Instruction>,
    val jumps: Map<Int, Int>,
    val frames: Map<Int, StackMapFrame>,
    val exceptionHandlers: List<ExceptionHandler>,
    val size: Int,
)

fun Fragment.bytecode(): ByteArray = BytecodeSerializer.emit(this).first

fun List<Fragment>.join(): Fragment {
    if (size == 1) return first()

    val instructions = mutableListOf<Instruction>()
    val jumps = mutableMapOf<Int, Int>()
    val frames = mutableMapOf<Int, StackMapFrame>()
    val exceptionHandlers = mutableListOf<ExceptionHandler>()
    var count = 0
    var size = 0

    forEach { fragment ->
        instructions.addAll(fragment.instructions)
        fragment.jumps.asSequence().map { (from, to) -> from + count to to + count }.forEach {
            jumps[it.first] = it.second
        }
        fragment.frames.asSequence().map { (from, frame) -> from + count to frame }.forEach {
            frames[it.first] = it.second
        }
        exceptionHandlers.addAll(fragment.exceptionHandlers)
        count += fragment.instructions.size
        size += fragment.size
    }

    return Fragment(instructions, jumps, frames, exceptionHandlers, size)
}
