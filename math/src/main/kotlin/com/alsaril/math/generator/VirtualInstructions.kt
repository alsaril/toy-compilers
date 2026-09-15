package com.alsaril.math.generator

import com.alsaril.codegen.classfile.code.CodeBuilder
import com.alsaril.codegen.classfile.code.Splice
import com.alsaril.codegen.classfile.code.fload

internal sealed interface VirtualInstruction

internal sealed interface VariableInstruction : VirtualInstruction {
    val index: Int
}

internal class LoadInstruction(override val index: Int) : VariableInstruction
internal class ExactInstruction(val code: Splice) : VirtualInstruction

internal class VirtualInstructionsBuilder {
    private val instructions = mutableListOf<VirtualInstruction>()
    private val statistics = mutableMapOf<Int, Counter>()

    fun nvars() = statistics.size

    fun append(code: Splice) {
        instructions.add(ExactInstruction(code))
    }

    fun load(index: Int) {
        instructions.add(LoadInstruction(index))
        statistics.computeIfAbsent(index) { Counter() }.inc()
    }

    fun extend(other: VirtualInstructionsBuilder) {
        instructions.addAll(other.instructions)
        other.statistics.forEach { (i, counter) ->
            statistics.merge(i, counter) { c1, c2 -> c1 + c2 }
        }
    }

    fun sortedVariables() = statistics.asSequence()
        .sortedByDescending { it.value.get() }
        .map { it.key }
        .toList()

    fun emitRealTransforming(codeBuilder: CodeBuilder, o2n: Map<Int, Int>, callSlots: Int) =
        with(codeBuilder) {
            instructions.forEach {
                when (it) {
                    is ExactInstruction -> append(it.code)
                    is LoadInstruction -> fload(o2n[it.index]!! + callSlots)
                }
            }
        }
}
