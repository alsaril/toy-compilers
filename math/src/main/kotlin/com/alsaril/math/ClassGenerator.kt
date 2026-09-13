package com.alsaril.math

import com.alsaril.codegen.classfile.ClassFileBuilder
import com.alsaril.codegen.classfile.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.Fragment
import com.alsaril.codegen.classfile.MethodAccessFlag.*
import com.alsaril.codegen.classfile.code.*
import com.alsaril.math.BinaryKind.*
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
        .generateEval(ast)
        .build()

    private fun variables(ast: Node): Map<String, Int> {
        val n2i = mutableMapOf<String, Int>()
        val stack = ArrayDeque<Node>()
        stack.addLast(ast)

        while (stack.isNotEmpty()) {
            when (val node = stack.removeLast()) {
                is Value -> {}
                is Var -> n2i.putIfAbsent(node.name, n2i.size)
                is Neg -> stack.addLast(node.arg)

                is Op -> {
                    stack.addLast(node.right)
                    stack.addLast(node.left)
                }
            }
        }

        return n2i
    }

    private class MutableInt {
        private var value = 0

        fun inc() = value++
    }

    private fun ClassFileBuilder.emitAccessor(variables: Map<String, Int>, callSlots: Int) = emitFragment {
        method("getFloat", "(Ljava/util/Map;Ljava/lang/String;)F", 4, PRIVATE, STATIC, FINAL) {
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

        variables.forEach { (name, index) ->
            aload(0)
            ldc(string(name))
            invokestatic(method(self(), "getFloat", "(Ljava/util/Map;Ljava/lang/String;)F"))
            fstore(index + callSlots)
        }

        maxStack(2)
    }

    private fun ClassFileBuilder.generateEval(ast: Node) = apply {
        val callSlots = 1
        val variables = variables(ast)
        val accessor = emitAccessor(variables, callSlots)
        val count = MutableInt()
        val body = materialize(ast, variables, accessor, callSlots, count)
        val (name, descriptor) = defineMethod(accessor, body, count)
        method("eval", "(Ljava/util/Map;)F", maxStack = 1, PUBLIC, FINAL) {
            aload(1)
            invokestatic(method(self(), name, descriptor))
            freturn()
        }
    }

    private fun ClassFileBuilder.defineMethod(
        accessor: Fragment,
        body: Fragment,
        count: MutableInt
    ): Pair<String, String> {
        val name = "f${count.inc()}"
        val descriptor = "(Ljava/util/Map;)F"
        method(
            name,
            descriptor,
            maxStack = 0,
            PUBLIC,
            STATIC,
            FINAL
        ) {
            fragment(accessor)
            fragment(body)
            freturn()
        }
        return name to descriptor
    }

    private data class Context(
        val codeBuilder: CodeBuilder,
        val maxStack: Int,
        val delta: Int
    ) {
        fun build(): Fragment = codeBuilder.apply { maxStack(maxStack) }.build()
    }

    private fun ClassFileBuilder.materialize(
        ast: Node,
        variables: Map<String, Int>,
        accessor: Fragment,
        callSlots: Int,
        count: MutableInt
    ): Fragment {
        val stack = mutableListOf<Pair<Node, MutableList<Context>>>()
        stack.add(ast to mutableListOf())

        var result: Fragment? = null

        fun ret(context: Context) {
            stack.removeLast()
            if (stack.isEmpty()) {
                result = context.build()
            } else {
                stack.last().second.add(context)
            }
        }

        fun outline(fragment: Fragment): Context {
            val (name, descriptor) = defineMethod(accessor, fragment, count)
            return Context(newCodeBuilder().apply {
                aload(0)
                invokestatic(method(self(), name, descriptor))
            }, 1, 1)
        }

        fun outlineIfSpills(context: Context): Context {
            val (builder, maxStack) = context
            if (builder.loc() < loadFactor * methodLengthLimit) return context
            return outline(context.build())
        }

        while (stack.isNotEmpty()) {
            val (node, results) = stack.last()
            when (node) {
                is Value -> node.value.let { value ->
                    newCodeBuilder().apply {
                        if (value.toRawBits() == 0 || value == 1.0f || value == 2.0f) {
                            fconst(value.toInt())
                        } else {
                            ldc(float(value))
                        }
                    }
                }.let { ret(Context(it, 1, 1)) }

                is Var -> newCodeBuilder()
                    .apply { fload(variables[node.name]!! + callSlots) }
                    .let { ret(Context(it, 1, 1)) }

                is Neg -> {
                    if (results.isEmpty()) {
                        stack.add(node.arg to mutableListOf())
                        continue
                    }

                    val result = results.first()
                    ret(outlineIfSpills(result).apply { codeBuilder.fneg() })
                }

                is Op -> {
                    when (results.size) {
                        0 -> stack.add(node.left to mutableListOf())
                        1 -> stack.add(node.right to mutableListOf())
                        else -> {
                            val (leftContext, rightContext) = results

                            var left = outlineIfSpills(leftContext)
                            var right = outlineIfSpills(rightContext)

                            // the sum can still overflow
                            if (left.codeBuilder.loc() + right.codeBuilder.loc() > loadFactor * methodLengthLimit) {
                                left = outline(left.build())
                                right = outline(right.build())
                            }

                            val builder = left.codeBuilder.apply { // asymmetric
                                fragment(right.build())
                                when (node.kind) {
                                    ADD -> fadd()
                                    SUB -> fsub()
                                    MUL -> fmul()
                                    DIV -> fdiv()
                                }
                            }
                            ret(
                                Context(
                                    builder,
                                    max(left.maxStack, left.delta + right.maxStack),
                                    left.delta + right.delta - 1
                                )
                            )
                        }
                    }
                }
            }
        }

        return result!!
    }
}