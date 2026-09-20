package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.DosWriter
import com.alsaril.codegen.classfile.Fragment
import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.classfile.attributes.StackMapFrame
import com.alsaril.codegen.classfile.code.instruction.Instruction
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import com.alsaril.codegen.write
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

@DslMarker
annotation class CodeDsl

@CodeDsl
class CodeBuilder(
    internal val cp: UpdatableConstantPool,
    internal val thisClass: String,
    internal val parentClass: String,
) {
    private val instructions = mutableListOf<Instruction>()
    private val jumps = mutableMapOf<Int, Int>()
    private val frames = mutableListOf<StackMapFrame>()
    private val exceptionHandlers = mutableListOf<ExceptionHandler>()
    private var size = 0

    private val buffer = ByteArrayOutputStream(3)
    private val writer = DosWriter(DataOutputStream(buffer))

    internal fun add(instruction: Instruction): Label {
        val index = instructions.size
        instructions.add(instruction)
        buffer.reset(); writer.write(instruction)
        size += buffer.size()
        return LabelImpl(index)
    }

    internal fun frame(frame: StackMapFrame) {
        frames.add(frame)
    }

    private data class LabelImpl(override val index: Int) : Label

    fun link(from: Label, dest: Label) {
        from as LabelImpl; dest as LabelImpl
        jumps[from.index] = dest.index
    }

    fun `catch`(from: Label, to: Label, handler: Label, type: ClassPointer?) {
        from as LabelImpl; to as LabelImpl; handler as LabelImpl
        exceptionHandlers.add(ExceptionHandler(from.index, to.index, handler.index, type?.index ?: 0))
    }

    fun fragment(fragment: Fragment) {
        val count = instructions.size
        instructions.addAll(fragment.instructions)
        fragment.jumps.asSequence().map { (from, to) -> from + count to to + count }.forEach {
            jumps[it.first] = it.second
        }
        fragment.frames
            .asSequence()
            .map { frame -> patchOffset(frame, frame.offsetDelta + count) }
            .forEach(frames::add)
        fragment.exceptionHandlers
            .asSequence()
            .map { it.shift(count) }
            .forEach(exceptionHandlers::add)
        size += fragment.size
    }

    @Suppress("UNCHECKED_CAST")
    fun build() = Fragment(
        instructions.toList(),
        jumps.toMap(),
        frames.toList(),
        exceptionHandlers.toList(),
        size
    )
}

sealed interface Label {
    val index: Int
}
