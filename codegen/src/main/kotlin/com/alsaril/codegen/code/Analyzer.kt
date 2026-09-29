package com.alsaril.codegen.code

import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.classfile.attributes.FullFrame
import com.alsaril.codegen.classfile.attributes.ObjectVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.*
import com.alsaril.codegen.classfile.attributes.UninitializedVariableInfo
import com.alsaril.codegen.classfile.parseFunctionDescriptor
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import com.alsaril.codegen.instruction.*
import com.alsaril.codegen.verification.*
import com.alsaril.codegen.verification.PrimitiveType.*
import java.util.*

internal class Analyzer(
    private val cp: UpdatableConstantPool,
    private val thisName: String,
    private val hierarchy: ClassHierarchy,
) {
    private data class Frame(val stack: List<VerificationType>, val locals: Locals)

    private fun frameFrom(descriptor: String, constructor: Boolean, static: Boolean): Frame {
        val self = when {
            static -> emptyList()
            constructor -> listOf(UNINITIALIZED_THIS)
            else -> listOf(ReferenceType(thisName))
        }
        val args = parseFunctionDescriptor(descriptor).args.map { it.verificationType }
        return Frame(stack = emptyList(), locals = Locals.of(self + args))
    }

    fun analyze(
        fragment: Fragment,
        descriptor: String,
        constructor: Boolean,
        static: Boolean,
    ): Triple<Int, Int, List<FullFrame>> {
        val instructions = fragment.instructions
        require(instructions.isNotEmpty()) { "a method body must hold at least one instruction" }

        val frames = Array<Frame?>(instructions.size) { null }
        val deque = ArrayDeque<Pair<Int, Frame>>()
        val framesIndexes = sortedSetOf<Int>()
        var maxLocals = 0
        val i2h = mutableMapOf<Int, MutableList<ExceptionHandler>>()
        fragment.exceptionHandlers.forEach { handler ->
            require(handler.handlerPc in frames.indices) {
                "a handler starts at ${handler.handlerPc}, which is past the last instruction"
            }
            framesIndexes.add(handler.handlerPc)
            (handler.startPc..<handler.endPc).forEach {
                i2h.computeIfAbsent(it) { mutableListOf() }.add(handler)
            }
        }

        deque.addLast(0 to frameFrom(descriptor, constructor, static)) // entry
        while (deque.isNotEmpty()) {
            val (pc, enterFrame) = deque.removeFirst()
            val frame = if (frames[pc] == null) {
                enterFrame
            } else if (frames[pc] != enterFrame) {
                val known = frames[pc]!!
                check(known.stack.size == enterFrame.stack.size) {
                    "${instructions[pc]} at $pc is reached with a stack ${known.stack.size} deep " +
                            "on one path and ${enterFrame.stack.size} deep on another"
                }
                val newFrame = checkNotNull(merge(enterFrame, known)) {
                    "${instructions[pc]} at $pc is reached with a stack of ${known.stack} " +
                            "on one path and ${enterFrame.stack} on another"
                }
                if (newFrame == known) continue
                newFrame
            } else continue

            frames[pc] = frame
            val instruction = instructions[pc]

            val nextPcs: List<Int> = nextPcs(instruction, pc, fragment.jumps)
            val nextFrame = if (instruction is DynamicInstruction ||
                instruction is invokespecial && instruction.constructorFor != null
            ) dynamicFrame(frame, instruction, pc) else {
                Frame(
                    stack = staticStack(frame.stack, instruction, pc),
                    locals = staticLocals(frame.locals, instruction, pc),
                )
            }

            maxLocals = maxOf(maxLocals, frame.locals.size, nextFrame.locals.size)

            if (instruction is JumpInstruction) {
                framesIndexes.add(fragment.jumps[pc]!!)
            }

            nextPcs.forEach {
                if (it in frames.indices) {
                    deque.addLast(it to nextFrame)
                    return@forEach
                }
                throw IllegalStateException("$instruction at $pc continues to $it, which is past the last instruction")
            }

            i2h[pc]?.forEach { handler ->
                val caught = listOf(ReferenceType(handler.catchType?.name ?: "java/lang/Throwable"))
                deque.addLast(handler.handlerPc to Frame(stack = caught, locals = enterFrame.locals))
                // a constructor call initializes every copy of its object, locals included, and
                // HotSpot checks the handler against the locals it leaves as well
                if (instruction is invokespecial && instruction.constructorFor != null) {
                    deque.addLast(handler.handlerPc to Frame(stack = caught, locals = nextFrame.locals))
                }
            }
        }

        frames.forEachIndexed { pc, frame -> require(frame != null) { "instruction $pc is unreachable" } }

        val maxStack = frames.asSequence().maxOfOrNull { it!!.stack.sumOf(VerificationType::slots) }!!
        val stackMapFrames = framesIndexes.map {
            FullFrame(it, frames[it]!!.locals.entries { type -> type.toInfo() }, frames[it]!!.stack.map { type -> type.toInfo() })
        }

        return Triple(maxStack, maxLocals, stackMapFrames)
    }

    private fun merge(frame1: Frame, frame2: Frame): Frame? {
        require(frame1.stack.size == frame2.stack.size) {
            "stacks ${frame1.stack.size} and ${frame2.stack.size} deep cannot be merged"
        }
        val stack = mutableListOf<VerificationType>()
        (frame1.stack.asSequence() zip frame2.stack.asSequence()).forEach { (s1, s2) ->
            val s = merge(s1, s2) ?: return null
            stack.add(s)
        }
        return Frame(stack = stack, locals = frame1.locals.merge(frame2.locals) { l1, l2 -> merge(l1, l2) })
    }

    private fun merge(t1: VerificationType, t2: VerificationType): VerificationType? {
        if (t1 == t2) return t1
        if (t1 == NULL && t2.isAssignableToReference) return t2
        if (t2 == NULL && t1.isAssignableToReference) return t1
        if (t1 is ReferenceType && t2 is ReferenceType) {
            return ReferenceType(hierarchy.commonSuperclass(t1.descriptor, t2.descriptor))
        }
        return null
    }

    private fun isAssignable(actual: VerificationType, expected: Expected): Boolean = when (expected) {
        is OfType -> actual == expected.type || expected.type is ReferenceType && (actual == NULL ||
                actual is ReferenceType && hierarchy.isAssignable(actual.descriptor, expected.type.descriptor))

        AnyReference -> actual.isAssignableToReference
        is OneOf -> expected.types.any { isAssignable(actual, OfType(it)) }
    }

    private fun requireLocal(locals: Locals, read: Read, instruction: Instruction, pc: Int) {
        val held = locals.read(read.index, instruction, pc)
        require(isAssignable(held, OfType(read.type))) {
            "$instruction at $pc reads local ${read.index} as ${read.type}, but it holds $held"
        }
    }

    private val VerificationType.fitsReferenceSlot
        get() = isAssignableToReference || this == UNINITIALIZED_THIS || this is Uninitialized

    private fun requireDepth(stack: List<VerificationType>, pops: Int, instruction: Instruction, pc: Int) =
        require(stack.size >= pops) { "$instruction at $pc pops $pops from a stack ${stack.size} deep" }

    private fun MutableList<VerificationType>.pop(
        expected: Expected,
        instruction: Instruction,
        pc: Int,
    ): VerificationType {
        val actual = removeLast()
        require(isAssignable(actual, expected)) {
            "$instruction at $pc expects $expected on the stack, but finds $actual"
        }
        return actual
    }

    private fun staticLocals(enterLocals: Locals, instruction: Instruction, pc: Int): Locals =
        instruction.localEffects().fold(enterLocals) { locals, effect ->
            when (effect) {
                is Read -> locals.also { requireLocal(it, effect, instruction, pc) }
                is Write -> locals.write(effect.index, effect.type)
            }
        }

    private fun staticStack(
        enterStack: List<VerificationType>,
        instruction: Instruction,
        pc: Int,
    ): List<VerificationType> {
        val effect = instruction.stackEffect()
        requireDepth(enterStack, effect.before.size, instruction, pc)
        val stack = enterStack.toMutableList()
        effect.before.asReversed().forEach { stack.pop(it, instruction, pc) }
        stack.addAll(effect.after)
        return stack
    }

    private fun dynamicFrame(
        enterFrame: Frame,
        instruction: Instruction,
        pc: Int,
    ): Frame = when (instruction) {
        is aload -> {
            val type = enterFrame.locals.read(instruction.index, instruction, pc)
            require(type.fitsReferenceSlot) {
                "$instruction at $pc expects a reference in local ${instruction.index}, but it holds $type"
            }
            enterFrame.copy(stack = enterFrame.stack.toMutableList().apply { add(type) })
        }

        is astore -> {
            requireDepth(enterFrame.stack, 1, instruction, pc)
            val stack = enterFrame.stack.toMutableList()
            val top = stack.removeLast()
            require(top.fitsReferenceSlot) { "$instruction at $pc stores a reference, but finds $top" }
            Frame(stack = stack, locals = enterFrame.locals.write(instruction.index, top))
        }

        dup -> {
            requireDepth(enterFrame.stack, 1, instruction, pc)
            val top = enterFrame.stack.last()
            require(top.slots == 1) { "$instruction at $pc copies a one slot value, but finds $top" }
            enterFrame.copy(stack = enterFrame.stack.toMutableList().apply { add(top) })
        }

        dup2 -> {
            requireDepth(enterFrame.stack, 1, instruction, pc)
            val top = enterFrame.stack.last()
            if (top.slots == 1) {
                requireDepth(enterFrame.stack, 2, instruction, pc)
                val second = enterFrame.stack[enterFrame.stack.size - 2]
                require(second.slots == 1) { "$instruction at $pc copies two one slot values, but finds $second under $top" }
                enterFrame.copy(
                    stack = enterFrame.stack.toMutableList().apply { add(second); add(top) })
            } else {
                enterFrame.copy(stack = enterFrame.stack.toMutableList().apply { add(top) })
            }
        }

        dup_x1 -> {
            requireDepth(enterFrame.stack, 2, instruction, pc)
            val stack = enterFrame.stack.toMutableList()
            val a = stack.removeLast()
            val b = stack.removeLast()
            require(a.slots == 1 && b.slots == 1) {
                "$instruction at $pc works on two one slot values, but finds $b and $a"
            }
            stack.add(a); stack.add(b); stack.add(a)
            enterFrame.copy(stack = stack)
        }

        dup_x2 -> {
            requireDepth(enterFrame.stack, 2, instruction, pc)
            val stack = enterFrame.stack.toMutableList()
            val a = stack.removeLast()
            require(a.slots == 1) { "$instruction at $pc copies a one slot value, but finds $a" }
            val b = stack.removeLast()
            if (b.slots == 2) {
                stack.add(a); stack.add(b); stack.add(a)
            } else {
                requireDepth(enterFrame.stack, 3, instruction, pc) // a one slot value under the top needs another
                val c = stack.removeLast()
                require(c.slots == 1) {
                    "$instruction at $pc reaches under $b, so it needs a one slot value there, but finds $c"
                }
                stack.add(a); stack.add(c); stack.add(b); stack.add(a)
            }
            enterFrame.copy(stack = stack)
        }

        is new -> {
            val stack = enterFrame.stack.toMutableList()
            stack.add(Uninitialized(pc))
            enterFrame.copy(stack = stack)
        }

        is invokespecial if instruction.constructorFor != null -> {
            requireDepth(enterFrame.stack, instruction.args.size, instruction, pc) // the receiver and the arguments
            val stack = enterFrame.stack.toMutableList()
            instruction.args.drop(1).asReversed().forEach { stack.pop(OfType(it), instruction, pc) } // the last argument is on top
            val top = stack.removeLast()
            require(top == UNINITIALIZED_THIS || top is Uninitialized) {
                "$instruction at $pc expects uninitializedThis or an object fresh from new under its arguments, " +
                        "but finds $top"
            }

            val replacement = if (top == UNINITIALIZED_THIS) ReferenceType(thisName) else instruction.constructorFor

            stack.forEachIndexed { index, type ->
                if (type == top) stack[index] = replacement
            }
            Frame(stack = stack, locals = enterFrame.locals.initialise(top, replacement))
        }

        else -> throw IllegalStateException("$instruction at $pc has no frame rule of its own")
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

    private fun VerificationType.toInfo() = when (this) {
        TOP -> TopVariableInfo
        INTEGER -> IntegerVariableInfo
        FLOAT -> FloatVariableInfo
        DOUBLE -> DoubleVariableInfo
        LONG -> LongVariableInfo
        NULL -> NullVariableInfo
        UNINITIALIZED_THIS -> UninitializedThis
        is ReferenceType -> ObjectVariableInfo(cp.putClass(descriptor))
        is Uninitialized -> UninitializedVariableInfo(offset)
        VOID -> throw IllegalStateException("$this describes no value, so no frame can hold it")
    }
}