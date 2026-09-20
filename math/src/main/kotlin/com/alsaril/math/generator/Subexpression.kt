package com.alsaril.math.generator

import com.alsaril.codegen.classfile.ClassFileBuilder
import com.alsaril.codegen.classfile.code.CodeBuilder
import com.alsaril.codegen.classfile.code.fload
import com.alsaril.codegen.classfile.code.instruction.FLoad

internal class Subexpression(private val bytecodeBuilder: CodeBuilder) {
    private val statistics = mutableMapOf<Int, Counter>()

    fun exact(call: CodeBuilder.() -> Unit): Subexpression {
        bytecodeBuilder.call()
        return this
    }

    fun fload(index: Int): Subexpression {
        bytecodeBuilder.fload(index + callSlots)
        statistics.computeIfAbsent(index) { Counter() }.inc()
        return this
    }

    val size: Int get() = bytecodeBuilder.size()

    val variableCount: Int get() = statistics.size

    fun build() = bytecodeBuilder.build()

    fun extend(other: Subexpression): Subexpression {
        bytecodeBuilder.fragment(other.build())
        other.statistics.forEach { (i, counter) ->
            statistics.merge(i, counter) { c1, c2 -> c1 + c2 }
        }
        return this
    }

    fun variablesByUse() = statistics.asSequence()
        .sortedByDescending { it.value.get() }
        .map { it.key }
        .toList()

    fun transform(o2n: Map<Int, Int>) {
        bytecodeBuilder.transform { _, instruction ->
            (instruction as? FLoad)?.let { FLoad(o2n[it.index - callSlots]!! + callSlots) }
        }
    }
}

internal fun ClassFileBuilder.subexpression() = Subexpression(newCodeBuilder())
