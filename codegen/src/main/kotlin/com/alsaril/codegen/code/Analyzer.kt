package com.alsaril.codegen.code

import com.alsaril.codegen.classfile.attributes.FullFrame
import com.alsaril.codegen.classfile.attributes.ObjectVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.*
import com.alsaril.codegen.classfile.attributes.StackMapFrame
import com.alsaril.codegen.classfile.attributes.UninitializedVariableInfo
import com.alsaril.codegen.classfile.parseFunctionDescriptor
import com.alsaril.codegen.instruction.*
import java.util.*

object Analyzer {
    private data class Frame(val locals: List<VerificationType>, val stack: List<VerificationType>)

    private fun ClassFileBuilder.frameFrom(descriptor: String, static: Boolean): Frame {
        val locals = mutableListOf<VerificationType>()
        if (!static) {
            locals.add(ReferenceType(thisName))
        }
        parseFunctionDescriptor(descriptor).args.forEach {
            locals.add(it.verificationType)
        }
        return Frame(locals, emptyList())
    }

    fun ClassFileBuilder.analyze(
        fragment: Fragment,
        descriptor: String,
        static: Boolean,
    ): Triple<Int, Int, List<StackMapFrame>> {
        val instructions = fragment.instructions
        require(instructions.isNotEmpty()) { "a method body must hold at least one instruction" }

        val frames = Array<Frame?>(instructions.size) { null }
        val deque = ArrayDeque<Pair<Int, Frame>>()
        val framesIndexes = mutableListOf<Int>()
        deque.addLast(0 to frameFrom(descriptor, static)) // entry
        fragment.exceptionHandlers.forEach { // handlers, entered with the throwable alone
            require(it.handlerPc in frames.indices) {
                "a handler starts at ${it.handlerPc}, which is past the last instruction"
            }
            deque.addLast(it.handlerPc to Frame(emptyList(), listOf(ReferenceType("java/lang/Throwable"))))
            framesIndexes.add(it.handlerPc)
        }

        while (deque.isNotEmpty()) {
            val (pc, enterFrame) = deque.removeFirst()
            if (frames[pc] == null) {
                frames[pc] = enterFrame
            } else if (frames[pc] != enterFrame) {
                throw IllegalStateException()
            } else continue

            val instruction = instructions[pc]

            val nextPcs: List<Int> = nextPcs(instruction, pc, fragment.jumps)
            val nextStack: List<VerificationType> = nextStack(enterFrame.stack, instruction)
            val nextLocals: List<VerificationType> = nextLocals(enterFrame.locals, instruction)

            if (instruction is JumpInstruction) {
                nextPcs.asSequence().filter { it != pc + 1 }.forEach {
                    framesIndexes.add(it)
                }
            }

            val nextFrame = Frame(nextLocals, nextStack)
            nextPcs.forEach {
                if (it in frames.indices) {
                    deque.addLast(it to nextFrame)
                }
                throw IllegalStateException("$instruction at $pc continues to $it, which is past the last instruction")
            }
        }

        frames.forEachIndexed { pc, frame -> require(frame != null) { "instruction $pc is unreachable" } }

        val maxStack = frames.asSequence().maxOfOrNull { it!!.stack.size }!!
        val maxLocals = frames.asSequence().maxOfOrNull { it!!.locals.size }!!
        val stackMapFrames =
            framesIndexes.map { FullFrame(it, frames[it]!!.locals.toInfo(), frames[it]!!.stack.toInfo()) }

        return Triple(maxStack, maxLocals, stackMapFrames)
    }

    fun nextLocals(
        locals: List<VerificationType>,
        instruction: Instruction
    ): List<VerificationType> {
        TODO()
    }

    private fun nextStack(
        enterStack: List<VerificationType>,
        instruction: Instruction,
    ): List<VerificationType> {
        val effects = instruction.stackEffects()
        val stack = enterStack.toMutableList()
        effects.forEach {
            when (it) {
                is Pop -> {
                    require(stack.isNotEmpty())
                    require(stack.last() == it.type)
                    stack.removeLast()
                }

                is Push -> stack.add(it.type)
            }
        }
        return stack
    }

    private fun nextPcs(instruction: Instruction, pc: Int, jumps: Map<Int, Int>): List<Int> {
        fun target() = requireNotNull(jumps[pc]) { "$instruction at $pc was never linked to a target" }

        return when (instruction) {
            goto -> listOf(target())
            is JumpInstruction -> listOf(pc + 1, target())
            `return`, areturn, ireturn, freturn, athrow -> emptyList()
            else -> listOf(pc + 1)
        }
    }

    private fun List<VerificationType>.toInfo() = map {
        when (it) {
            PrimitiveType.TOP -> TopVariableInfo
            PrimitiveType.INTEGER -> IntegerVariableInfo
            PrimitiveType.FLOAT -> FloatVariableInfo
            PrimitiveType.DOUBLE -> DoubleVariableInfo
            PrimitiveType.LONG -> LongVariableInfo
            PrimitiveType.NULL -> NullVariableInfo
            PrimitiveType.UNINITIALIZED_THIS -> UninitializedThis
            is ReferenceType -> ObjectVariableInfo(it.descriptorIndex!!)
            is Uninitialized -> UninitializedVariableInfo(it.offset)
            PrimitiveType.VOID, AnyReference -> throw IllegalStateException()
        }
    }
}