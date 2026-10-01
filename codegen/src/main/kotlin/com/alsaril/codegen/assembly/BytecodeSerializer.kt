package com.alsaril.codegen.assembly

import com.alsaril.codegen.DosWriter
import com.alsaril.codegen.classfile.attributes.*
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import com.alsaril.codegen.instruction.Fragment
import com.alsaril.codegen.instruction.JumpInstruction
import com.alsaril.codegen.write
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

object BytecodeSerializer {
    fun serialize(
        cp: UpdatableConstantPool,
        thisName: String,
        hierarchy: ClassHierarchy,
        fragment: Fragment,
        descriptor: String,
        constructor: Boolean,
        static: Boolean,
    ): CodeAttribute {
        val (maxStack, maxLocals, frames) =
            Analyzer(cp, thisName, hierarchy).analyze(fragment, descriptor, constructor, static)
        val (bytecode, stackMapFrames, exceptionHandlers) = emit(fragment, frames)

        return CodeAttribute(
            cp.putUtf8("Code"),
            maxStack,
            maxLocals,
            bytecode,
            exceptionHandlers,
            listOf(StackMapTableAttribute(cp.putUtf8("StackMapTable"), stackMapFrames)),
        )
    }

    internal fun emit(
        fragment: Fragment,
        frames: List<FullFrame>,
    ): Triple<ByteArray, List<StackMapFrame>, List<ExceptionHandler>> {
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
        val patchedFrames = frames.map { frame ->
            val loc = loc(frame.offsetDelta, "a frame")
            frame.patchUninitialized(i2loc)
                .patchOffset(loc - prev - 1)
                .also { prev = loc }
        }

        val exceptionHandlers = fragment.exceptionHandlers.map {
            it.copy(
                startPc = loc(it.startPc, "a guarded range"),
                endPc = if (it.endPc == fragment.instructions.size) code.size
                else loc(it.endPc, "a guarded range"),
                handlerPc = loc(it.handlerPc, "a handler"),
            )
        }

        return Triple(code, patchedFrames, exceptionHandlers)
    }

    internal fun ByteArray.s2At(pos: Int, value: Int) {
        require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "$value does not fit an s2" }
        this[pos] = (value shr 8).toByte()
        this[pos + 1] = value.toByte()
    }
}