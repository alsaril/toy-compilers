package com.alsaril.math.generator

import com.alsaril.codegen.classfile.ClassFileBuilder
import com.alsaril.codegen.classfile.Fragment
import com.alsaril.codegen.classfile.MethodAccessFlag.*
import com.alsaril.codegen.classfile.code.*
import com.alsaril.math.*
import com.alsaril.math.BinaryKind.*
import com.alsaril.math.generator.Context.Companion.context
import kotlin.math.max
import kotlin.math.min

private const val methodLengthLimit = 8000 // hotspot threshold, however can be as big as 65535
// a merge checks the sum of two preludes rather than the prelude of their union, which
// over-counts when they share variables and under-counts when their union crosses into a
// wider store band. The excess saturates at 513 bytes rather than growing with the
// expression — the README derives it — so holding the budget that far below the limit
// covers it by construction
private const val mergeExcess = 513
private const val bodyLengthLimit = methodLengthLimit - mergeExcess

private const val callSlots = 1

private const val descriptor = "(Ljava/util/Map;)F"

internal data class Context(
    val bytecodeBuilder: CodeBuilder,
    val virtualInstructionsBuilder: VirtualInstructionsBuilder,
    val maxStack: Int,
    val delta: Int,
) {
    fun build(): Fragment = bytecodeBuilder.apply { maxStack(maxStack) }.build()

    fun exact(call: CodeBuilder.() -> Unit): Context {
        val start = bytecodeBuilder.loc()
        bytecodeBuilder.call()
        val end = bytecodeBuilder.loc()
        virtualInstructionsBuilder.append(bytecodeBuilder.splice(start, end))
        return this
    }

    fun fload(index: Int): Context {
        bytecodeBuilder.fload(index + callSlots)
        virtualInstructionsBuilder.load(index)
        return this
    }

    companion object {
        fun ClassFileBuilder.context(maxStack: Int, delta: Int) =
            Context(newCodeBuilder(), VirtualInstructionsBuilder(), maxStack, delta)
    }
}

internal fun ClassFileBuilder.generateEval(ast: Node) = apply {
    val (n2i, vars) = variables(ast)
    val counter = Counter()
    val context = materialize(ast, vars, n2i, counter)
    val (name, _) = defineMethod(context, vars, counter)

    method("eval", descriptor, PUBLIC, FINAL) {
        aload(1)
        invokestatic(smethod(self(), name, descriptor))
        freturn()
    }
}

private fun CodeBuilder.accessorLine(name: String, slot: Int) {
    aload(0)
    ldc(string(name))
    invokestatic(smethod(self(), "getFloat", "(Ljava/util/Map;Ljava/lang/String;)F"))
    fstore(slot + callSlots)
}

private fun CodeBuilder.emitAccessor(vars: List<String>, variables: List<Int>) {
    variables.forEachIndexed { index, old -> accessorLine(vars[old], index) }
}

private fun ClassFileBuilder.preludeLines(vars: List<String>): IntArray {
    val cheapest = vars.firstOrNull() ?: return IntArray(3)

    // a slot of 0, 3 and 255 lands in each of the three widths, once callSlots is added
    return intArrayOf(0, 3, 255)
        .map { slot -> newCodeBuilder().apply { accessorLine(cheapest, slot) }.loc() + 1 }
        .toIntArray()
}

private fun preludeLength(lines: IntArray, count: Int): Int {
    val compact = min(count, 3)                 // slots 1..3
    val operand = max(min(count, 255) - 3, 0)   // slots 4..255
    val wide = max(count - 255, 0)              // slots 256 and up

    return lines[0] * compact + lines[1] * operand + lines[2] * wide
}

private fun ClassFileBuilder.defineMethod(
    context: Context,
    vars: List<String>,
    counter: Counter,
): Pair<String, String> {
    val name = "f${counter.inc()}"

    method(name, descriptor, PRIVATE, STATIC, FINAL) {
        val variables = context.virtualInstructionsBuilder.sortedVariables()
        val o2n = variables.asSequence().mapIndexed { index, old -> old to index }.toMap()

        emitAccessor(vars, variables)
        context.virtualInstructionsBuilder.emitRealTransforming(this, o2n, callSlots)
        freturn()
    }

    return name to descriptor
}

private fun ClassFileBuilder.materialize(
    ast: Node,
    vars: List<String>,
    variables: Map<String, Int>,
    counter: Counter,
): Context {
    val lines = preludeLines(vars)
    fun length(subtree: Context) = subtree.bytecodeBuilder.loc() +
        preludeLength(lines, subtree.virtualInstructionsBuilder.nvars())

    val stack = mutableListOf<Pair<Node, MutableList<Context>>>()
    stack.add(ast to mutableListOf())

    var result: Context? = null

    fun ret(context: Context) {
        stack.removeLast()
        if (stack.isEmpty()) result = context else stack.last().second.add(context)
    }

    fun outline(subtree: Context): Context {
        val (name, _) = defineMethod(subtree, vars, counter)
        return context(1, 1).exact {
            aload(0)
            invokestatic(smethod(self(), name, descriptor))
        }
    }

    fun outlineIfSpills(subtree: Context) =
        if (length(subtree) <= bodyLengthLimit) subtree
        else outline(subtree)

    while (stack.isNotEmpty()) {
        val (node, results) = stack.last()
        when (node) {
            is Value -> context(1, 1).exact {
                val value = node.value
                if (value.toRawBits() == 0 || value == 1.0f || value == 2.0f) {
                    fconst(value.toInt())
                } else {
                    ldc(float(value))
                }
            }.let(::ret)

            is Var -> context(1, 1).fload(variables[node.name]!!).let(::ret)

            is Neg -> {
                if (results.isEmpty()) {
                    stack.add(node.arg to mutableListOf())
                    continue
                }

                ret(outlineIfSpills(results.first()).exact { fneg() })
            }

            is Op -> {
                when (results.size) {
                    0 -> stack.add(node.left to mutableListOf())
                    1 -> stack.add(node.right to mutableListOf())
                    else -> null
                }?.let { continue }

                val (leftResult, rightResult) = results
                var left = outlineIfSpills(leftResult)
                var right = outlineIfSpills(rightResult)

                if (length(left) + length(right) > bodyLengthLimit) {
                    left = outline(left)
                    right = outline(right)
                }

                ret(
                    Context(
                        left.bytecodeBuilder.apply { fragment(right.build()) },
                        left.virtualInstructionsBuilder.apply { extend(right.virtualInstructionsBuilder) },
                        max(left.maxStack, left.delta + right.maxStack),
                        left.delta + right.delta - 1,
                    ).exact {
                        when (node.kind) {
                            ADD -> fadd()
                            SUB -> fsub()
                            MUL -> fmul()
                            DIV -> fdiv()
                        }
                    }
                )
            }
        }
    }

    return result!!
}
