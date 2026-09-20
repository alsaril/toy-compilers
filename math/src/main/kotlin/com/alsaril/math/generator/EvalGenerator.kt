package com.alsaril.math.generator

import com.alsaril.codegen.classfile.ClassFileBuilder
import com.alsaril.codegen.classfile.MethodAccessFlag.*
import com.alsaril.codegen.classfile.code.*
import com.alsaril.math.*
import com.alsaril.math.BinaryKind.*
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

// slot 0 holds the Map argument, so a variable's slot is its index plus this
internal const val callSlots = 1

private const val descriptor = "(Ljava/util/Map;)F"

internal fun ClassFileBuilder.generateEval(ast: Node) = apply {
    val (n2i, vars) = variables(ast)
    val counter = Counter()
    val body = materialize(ast, vars, n2i, counter)
    val (name, _) = defineMethod(body, vars, counter)

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
        .map { slot -> newCodeBuilder().apply { accessorLine(cheapest, slot) }.size() + 1 }
        .toIntArray()
}

private fun preludeLength(lines: IntArray, count: Int): Int {
    val compact = min(count, 3)                 // slots 1..3
    val operand = max(min(count, 255) - 3, 0)   // slots 4..255
    val wide = max(count - 255, 0)              // slots 256 and up

    return lines[0] * compact + lines[1] * operand + lines[2] * wide
}

private fun ClassFileBuilder.defineMethod(
    body: Subexpression,
    vars: List<String>,
    counter: Counter,
): Pair<String, String> {
    val name = "f${counter.inc()}"

    method(name, descriptor, PRIVATE, STATIC, FINAL) {
        val variables = body.variablesByUse()
        val o2n = variables.asSequence().mapIndexed { index, old -> old to index }.toMap()

        emitAccessor(vars, variables)
        body.transform(o2n)
        fragment(body.build())
        freturn()
    }

    return name to descriptor
}

private fun ClassFileBuilder.materialize(
    ast: Node,
    vars: List<String>,
    variables: Map<String, Int>,
    counter: Counter,
): Subexpression {
    val lines = preludeLines(vars)
    fun length(subtree: Subexpression) = subtree.size + preludeLength(lines, subtree.variableCount)

    val stack = mutableListOf<Pair<Node, MutableList<Subexpression>>>()
    stack.add(ast to mutableListOf())

    var result: Subexpression? = null

    fun ret(subexpression: Subexpression) {
        stack.removeLast()
        if (stack.isEmpty()) result = subexpression else stack.last().second.add(subexpression)
    }

    fun outline(subtree: Subexpression): Subexpression {
        val (name, _) = defineMethod(subtree, vars, counter)
        return subexpression().exact {
            aload(0)
            invokestatic(smethod(self(), name, descriptor))
        }
    }

    fun outlineIfSpills(subtree: Subexpression) =
        if (length(subtree) <= bodyLengthLimit) subtree
        else outline(subtree)

    while (stack.isNotEmpty()) {
        val (node, results) = stack.last()
        when (node) {
            is Value -> subexpression().exact {
                val value = node.value
                if (value.toRawBits() == 0 || value == 1.0f || value == 2.0f) {
                    fconst(value.toInt())
                } else {
                    ldc(float(value))
                }
            }.let(::ret)

            is Var -> subexpression().fload(variables[node.name]!!).let(::ret)

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
                    left.extend(right).exact {
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
