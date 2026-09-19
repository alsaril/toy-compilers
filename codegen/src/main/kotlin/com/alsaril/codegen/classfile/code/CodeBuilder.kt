package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.DosWriter
import com.alsaril.codegen.classfile.Fragment
import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.classfile.attributes.StackMapFrame
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import com.alsaril.codegen.write
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.*

@DslMarker
annotation class CodeDsl

@CodeDsl
class CodeBuilder(
    internal val cp: UpdatableConstantPool,
    internal val thisClass: String,
    internal val parentClass: String,
) {
    private val instructions = mutableListOf<Instruction>()
    private val jumps = IdentityHashMap<Instruction, Instruction>()
    private val frames = IdentityHashMap<Instruction, StackMapFrame>()
    private val exceptionHandlers = mutableListOf<ExceptionHandler>()
    private var size = 0

    private val buffer = ByteArrayOutputStream(3)
    private val writer = DosWriter(DataOutputStream(buffer))

    internal fun add(instruction: Instruction): Instruction {
        instructions.add(instruction)
        buffer.reset(); writer.write(instruction)
        size += buffer.size()
        return instruction
    }

    internal fun frame(instruction: Instruction, frame: StackMapFrame) {
        frames[instruction] = frame
    }

    internal fun addExceptionHandler(exceptionHandler: ExceptionHandler) {
        exceptionHandlers.add(exceptionHandler)
    }

    fun link(jump: Instruction, dest: Instruction) {
        jumps[jump] = dest
    }

    fun fragment(fragment: Fragment) {
        instructions.addAll(fragment.instructions)
        jumps.putAll(fragment.jumps)
        frames.putAll(fragment.frames)
        exceptionHandlers.addAll(fragment.exceptionHandlers)
        size += fragment.size
    }

    @Suppress("UNCHECKED_CAST")
    fun build() = Fragment(
        instructions.toList(),
        jumps.clone() as Map<Instruction, Instruction>,
        frames.clone() as Map<Instruction, StackMapFrame>,
        exceptionHandlers.toList(),
        size
    )
}
