package com.alsaril.codegen.classfile

import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.classfile.attributes.StackMapFrame
import com.alsaril.codegen.classfile.code.BytecodeSerializer
import com.alsaril.codegen.classfile.code.Instruction
import java.util.*


data class Fragment(
    val instructions: List<Instruction>,
    val jumps: Map<Instruction, Instruction>, // backed by identity
    val frames: Map<Instruction, StackMapFrame>, // backed by identity
    val exceptionHandlers: List<ExceptionHandler>,
    val size: Int,
)

fun Fragment.bytecode(): ByteArray = BytecodeSerializer.emit(this).first

fun List<Fragment>.join(): Fragment {
    if (size == 1) return first()

    val instructions = mutableListOf<Instruction>()
    val jumps = IdentityHashMap<Instruction, Instruction>()
    val frames = IdentityHashMap<Instruction, StackMapFrame>()
    val exceptionHandlers = mutableListOf<ExceptionHandler>()
    var size = 0

    forEach { fragment ->
        instructions.addAll(fragment.instructions)
        jumps.putAll(fragment.jumps)
        frames.putAll(fragment.frames)
        exceptionHandlers.addAll(fragment.exceptionHandlers)
        size += fragment.size
    }

    return Fragment(instructions, jumps, frames, exceptionHandlers, size)
}
