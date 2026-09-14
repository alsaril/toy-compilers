package com.alsaril.math

import com.alsaril.codegen.classfile.ClassFileBuilder
import com.alsaril.codegen.classfile.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.Fragment
import com.alsaril.codegen.classfile.MethodAccessFlag.*
import com.alsaril.codegen.classfile.code.*
import com.alsaril.math.BinaryKind.*
import com.alsaril.math.ClassGenerator.Context.Companion.new
import kotlin.math.max

object ClassGenerator {

    private const val methodLengthLimit = 8000 // hotspot threshold, however can be as big as 65535
    private const val loadFactor = 0.9

    fun generate(ast: Node) = classFile("Impl", parent = "java/lang/Object")
        .iface("com/alsaril/math/Program")
        .method("<init>", "()V", maxStack = 1, PUBLIC) {
            aload(0)
            invokespecial(method(parent(), "<init>", "()V"))
            `return`()
        }
        .method("getFloat", "(Ljava/util/Map;Ljava/lang/String;)F", 4, PRIVATE, STATIC, FINAL) {
            aload(0)
            aload(1)
            invokeinterface(imethod(clazz("java/util/Map"), "get", "(Ljava/lang/Object;)Ljava/lang/Object;"), 2)
            dup()
            instanceof(clazz("java/lang/Float"))
            val err = ifeq()
            checkcast(clazz("java/lang/Float"))
            invokevirtual(method(clazz("java/lang/Float"), "floatValue", "()F"))
            freturn()
            err(loc())
            frameStack(objInfo("java/lang/Object"))
            new(clazz("java/util/NoSuchElementException"))
            dup()
            aload(1)
            invokespecial(method(clazz("java/util/NoSuchElementException"), "<init>", "(Ljava/lang/String;)V"))
            athrow()
        }
        .generateEval(ast)
        .build()

    private fun variables(ast: Node): Pair<Map<String, Int>, List<String>> {
        val vars = mutableSetOf<String>()
        val stack = ArrayDeque<Node>()
        stack.addLast(ast)

        while (stack.isNotEmpty()) {
            when (val node = stack.removeLast()) {
                is Value -> {}
                is Var -> vars.add(node.name)
                is Neg -> stack.addLast(node.arg)

                is Op -> {
                    stack.addLast(node.right)
                    stack.addLast(node.left)
                }
            }
        }

        val list = vars.toList()
        val n2i = list.asSequence().mapIndexed { index, name -> name to index }.toMap()
        return n2i to list
    }

    private class Counter(private var value: Int = 0) {
        fun inc() = value++
        fun get() = value

        operator fun plus(other: Counter) = Counter(value + other.value)
    }

    private fun CodeBuilder.emitAccessor(vars: List<String>, variables: List<Int>, callSlots: Int) {
        variables.forEachIndexed { index, old ->
            aload(0)
            ldc(string(vars[old]))
            invokestatic(method(self(), "getFloat", "(Ljava/util/Map;Ljava/lang/String;)F"))
            fstore(index + callSlots)
        }

        maxStack(2)
    }

    private fun ClassFileBuilder.generateEval(ast: Node) = apply {
        val callSlots = 1
        val (n2i, vars) = variables(ast)
        val counter = Counter()
        val context = materialize(ast, vars, n2i, callSlots, counter)
        val (name, descriptor) = defineMethod(context, vars, callSlots, counter)
        method("eval", "(Ljava/util/Map;)F", maxStack = 1, PUBLIC, FINAL) {
            aload(1)
            invokestatic(method(self(), name, descriptor))
            freturn()
        }
    }

    private fun ClassFileBuilder.defineMethod(
        context: Context,
        vars: List<String>,
        callSlots: Int,
        counter: Counter
    ): Pair<String, String> {
        val name = "f${counter.inc()}"
        val descriptor = "(Ljava/util/Map;)F"
        method(
            name,
            descriptor,
            maxStack = 0,
            PUBLIC,
            STATIC,
            FINAL
        ) {
            // we're gonna compact the used variables based on their usage
            val variables = context.virtualInstructionsBuilder.sortedVariables()
            val o2n = variables.asSequence().mapIndexed { index, i -> i to index }.toMap()
            emitAccessor(vars, variables, callSlots)
            context.virtualInstructionsBuilder.emitRealTransforming(this, o2n, callSlots)
            maxStack(context.maxStack)
            freturn()
        }
        return name to descriptor
    }

    private sealed interface VirtualInstruction

    private sealed interface VariableInstruction : VirtualInstruction {
        val index: Int
    }

    private class LoadInstruction(override val index: Int) : VariableInstruction
    private class StoreInstruction(override val index: Int) : VariableInstruction
    private class ExactInstruction(val code: MutableList<Byte>) : VirtualInstruction

    private class VirtualInstructionsBuilder {
        private val instructions = mutableListOf<VirtualInstruction>()
        private val statistics = mutableMapOf<Int, Counter>()

        fun append(code: List<Byte>) {
            if (instructions.isNotEmpty() && instructions.last() is ExactInstruction) {
                (instructions.last() as ExactInstruction).code.addAll(code)
            } else {
                instructions.add(ExactInstruction(code.toMutableList()))
            }
        }

        fun recordUsage(index: Int) {
            statistics.computeIfAbsent(index) { Counter() }.inc()
        }

        fun load(index: Int) {
            instructions.add(LoadInstruction(index))
            recordUsage(index)
        }

        fun store(index: Int) {
            instructions.add(StoreInstruction(index))
            recordUsage(index)
        }

        fun extend(other: VirtualInstructionsBuilder) {
            instructions.addAll(other.instructions)
            other.statistics.forEach { (i, counter) ->
                statistics.merge(i, counter) { c1, c2 -> c1 + c2 }
            }
        }

        fun sortedVariables() = statistics.asSequence().sortedByDescending { it.value.get() }.map { it.key }.toList()

        fun emitRealTransforming(codeBuilder: CodeBuilder, o2n: Map<Int, Int>, callSlots: Int) = with(codeBuilder) {
            instructions.forEach {
                when (it) {
                    is ExactInstruction -> append(it.code)
                    is LoadInstruction -> fload(o2n[it.index]!! + callSlots)
                    is StoreInstruction -> fstore(o2n[it.index]!! + callSlots)
                }
            }
        }
    }

    private data class Context(
        val bytecodeBuilder: CodeBuilder, // estimate for splitting
        val virtualInstructionsBuilder: VirtualInstructionsBuilder,
        val maxStack: Int,
        val delta: Int,
        val callSlots: Int,
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
            fun ClassFileBuilder.new(maxStack: Int, delta: Int, callSlots: Int) = Context(
                newCodeBuilder(), VirtualInstructionsBuilder(), maxStack, delta, callSlots
            )
        }
    }

    private fun ClassFileBuilder.materialize(
        ast: Node,
        vars: List<String>,
        variables: Map<String, Int>,
        callSlots: Int,
        counter: Counter
    ): Context {
        val stack = mutableListOf<Pair<Node, MutableList<Context>>>()
        stack.add(ast to mutableListOf())

        var result: Context? = null

        fun ret(context: Context) {
            stack.removeLast()
            if (stack.isEmpty()) result = context else stack.last().second.add(context)
        }

        fun outline(context: Context): Context {
            val (name, descriptor) = defineMethod(context, vars, callSlots, counter)
            return new(1, 1, callSlots).exact {
                aload(0)
                invokestatic(method(self(), name, descriptor))
            }
        }

        fun outlineIfSpills(context: Context): Context {
            val (builder) = context
            if (builder.loc() < loadFactor * methodLengthLimit) return context
            return outline(context)
        }

        while (stack.isNotEmpty()) {
            val (node, results) = stack.last()
            when (node) {
                is Value -> node.value.let { value ->
                    new(1, 1, callSlots).exact {
                        if (value.toRawBits() == 0 || value == 1.0f || value == 2.0f) {
                            fconst(value.toInt())
                        } else {
                            ldc(float(value))
                        }
                    }
                }.let(::ret)

                is Var -> new(1, 1, callSlots)
                    .fload(variables[node.name]!!)
                    .let { ret(it) }

                is Neg -> {
                    if (results.isEmpty()) {
                        stack.add(node.arg to mutableListOf())
                        continue
                    }

                    val result = results.first()
                    ret(outlineIfSpills(result).exact { fneg() })
                }

                is Op -> {
                    when (results.size) {
                        0 -> stack.add(node.left to mutableListOf())
                        1 -> stack.add(node.right to mutableListOf())
                        else -> null
                    }?.let { continue }

                    val (leftContext, rightContext) = results

                    var left = outlineIfSpills(leftContext)
                    var right = outlineIfSpills(rightContext)

                    // the sum can still overflow
                    if (left.bytecodeBuilder.loc() + right.bytecodeBuilder.loc() > loadFactor * methodLengthLimit) {
                        left = outline(left)
                        right = outline(right)
                    }

                    // merge here we go
                    val context = Context(
                        left.bytecodeBuilder.apply { fragment(right.build()) },
                        left.virtualInstructionsBuilder.apply {
                            extend(right.virtualInstructionsBuilder)
                        },
                        max(left.maxStack, left.delta + right.maxStack),
                        left.delta + right.delta - 1,
                        callSlots,
                    ).exact {
                        when (node.kind) {
                            ADD -> fadd()
                            SUB -> fsub()
                            MUL -> fmul()
                            DIV -> fdiv()
                        }
                    }
                    ret(context)
                }
            }
        }

        return result!!
    }
}