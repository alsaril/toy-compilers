package com.alsaril.math

import com.alsaril.codegen.classfile.ClassFileBuilder
import com.alsaril.codegen.classfile.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.Fragment
import com.alsaril.codegen.classfile.MethodAccessFlag.*
import com.alsaril.codegen.classfile.code.*
import com.alsaril.math.BinaryKind.*

object ClassGenerator {

    private const val methodLengthLimit = 8000 // hotspot threshold, however can be as big as 65535
    private const val loadFactor = 0.9

    fun generate(ast: Node) = classFile("Impl", parent = "java/lang/Object")
        .iface("com/alsaril/math/Program")
        .method("<init>", "()V", maxStack = 1, maxLocals = 1, PUBLIC) {
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
        method("getFloat", "(Ljava/util/Map;Ljava/lang/String;)F", 4, 2, PRIVATE, STATIC, FINAL) {
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
    }

    private fun ClassFileBuilder.generateEval(ast: Node) = apply {
        val callSlots = 1
        val variables = variables(ast)
        val accessor = emitAccessor(variables, callSlots)
        val count = MutableInt()
        val body = materialize(ast, variables, accessor, callSlots, count)
        val (name, descriptor) = defineMethod(variables, accessor, body, callSlots, count)
        method("eval", "(Ljava/util/Map;)F", maxStack = 1, maxLocals = 2, PUBLIC, FINAL) {
            aload(1)
            invokestatic(method(self(), name, descriptor))
            freturn()
        }
    }

    private fun ClassFileBuilder.defineMethod(
        variables: Map<String, Int>,
        accessor: Fragment,
        body: Fragment,
        callSlots: Int,
        count: MutableInt
    ): Pair<String, String> {
        val name = "f${count.inc()}"
        val descriptor = "(Ljava/util/Map;)F"
        method(
            name,
            descriptor,
            maxStack = 1000, // todo deduce
            maxLocals = variables.size + callSlots,
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

    private fun ClassFileBuilder.materialize(
        ast: Node,
        variables: Map<String, Int>,
        accessor: Fragment,
        callSlots: Int,
        count: MutableInt
    ): Fragment {
        val stack = mutableListOf<Pair<Node, MutableList<CodeBuilder>>>()
        stack.add(ast to mutableListOf())

        var result: CodeBuilder? = null

        fun ret(codeBuilder: CodeBuilder) {
            stack.removeLast()
            if (stack.isEmpty()) {
                result = codeBuilder
            } else {
                stack.last().second.add(codeBuilder)
            }
        }

        fun outline(codeBuilder: CodeBuilder): CodeBuilder {
            val (name, descriptor) = defineMethod(variables, accessor, codeBuilder.build(), callSlots, count)
            return newCodeBuilder().apply {
                aload(0)
                invokestatic(method(self(), name, descriptor))
            }
        }

        fun outlineIfSpills(codeBuilder: CodeBuilder) = if (codeBuilder.loc() > loadFactor * methodLengthLimit) {
            // todo actually it will be longer as there is accessor in the beginning of the method
            // we can unpack only required variables and patch the body -- generate `iload 128` first
            // or materialize already wellformed code
            // but how to know the limits -- use ranges? and so that max < limit?
            outline(codeBuilder)
        } else codeBuilder

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
                }.let(::ret)

                is Var -> newCodeBuilder()
                    .apply { fload(variables[node.name]!! + callSlots) }
                    .let(::ret)

                is Neg -> {
                    if (results.isEmpty()) {
                        stack.add(node.arg to mutableListOf())
                        continue
                    }

                    outlineIfSpills(results.first())
                        .apply { fneg() }
                        .let(::ret)
                }

                is Op -> {
                    when (results.size) {
                        0 -> stack.add(node.left to mutableListOf())
                        1 -> stack.add(node.right to mutableListOf())
                        else -> {
                            val (rawLeft, rawRight) = results
                            val left: CodeBuilder
                            val right: CodeBuilder
                            if (rawLeft.loc() + rawRight.loc() > loadFactor * methodLengthLimit) {
                                left = outline(rawLeft)
                                right = outline(rawRight)
                            } else {
                                left = outlineIfSpills(rawLeft)
                                right = outlineIfSpills(rawRight)
                            }
                            left.apply { // asymmetric
                                fragment(right.build())
                                when (node.kind) {
                                    ADD -> fadd()
                                    SUB -> fsub()
                                    MUL -> fmul()
                                    DIV -> fdiv()
                                }
                            }.let(::ret)
                        }
                    }
                }
            }
        }

        return result!!.build()
    }
}