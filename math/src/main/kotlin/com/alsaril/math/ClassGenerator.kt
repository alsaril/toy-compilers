package com.alsaril.math

import com.alsaril.codegen.classfile.ClassFileBuilder
import com.alsaril.codegen.classfile.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.MethodAccessFlag.FINAL
import com.alsaril.codegen.classfile.MethodAccessFlag.PUBLIC
import com.alsaril.codegen.classfile.code.*
import com.alsaril.math.BinaryKind.*
import kotlin.math.max

object ClassGenerator {
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

    private fun ClassFileBuilder.generateEval(ast: Node) = apply {
        val n2i = variables(ast)
        method("eval", "(Ljava/util/Map;)F", maxStack = 4, maxLocals = 2 + n2i.size, PUBLIC, FINAL) {
            val locals = mutableListOf<VarInfo>().apply {
                add(objInfo(self())); add(objInfo(clazz("java/util/Map")))
            }
            n2i.forEach { (name, index) ->
                aload(1)
                ldc(string(name))
                invokeinterface(imethod(clazz("java/util/Map"), "get", "(Ljava/lang/Object;)Ljava/lang/Object;"), 2)
                dup()
                instanceof(clazz("java/lang/Float"))
                val exists = ifne()

                new(clazz("java/util/NoSuchElementException"))
                dup()
                ldc(string(name))
                invokespecial(method(clazz("java/util/NoSuchElementException"), "<init>", "(Ljava/lang/String;)V"))
                athrow()

                exists(loc())
                frameFull(locals, listOf(objInfo("java/lang/Object")))
                checkcast(clazz("java/lang/Float"))
                invokevirtual(method(clazz("java/lang/Float"), "floatValue", "()F"))
                fstore(index + 2)
                locals.add(FloatInfo)
            }

            class MutableInt(var value: Int = 0) {
                fun inc() = value++
            }

            val stack = mutableListOf<Pair<Node, MutableInt>>()
            stack.add(ast to MutableInt())

            fun ret() {
                stack.removeLast()
                stack.lastOrNull()?.second?.inc()
            }

            var currStack = 0
            var maxStack = 0

            while (stack.isNotEmpty()) {
                val (node, visited) = stack.last()
                when (node) {
                    is Neg -> if (visited.value == 0) {
                        stack.add(node.arg to MutableInt())
                    } else {
                        fneg()
                        ret()
                    }

                    is Op -> when (visited.value) {
                        0 -> stack.add(node.left to MutableInt())
                        1 -> stack.add(node.right to MutableInt())
                        else -> {
                            when (node.kind) {
                                ADD -> fadd()
                                SUB -> fsub()
                                MUL -> fmul()
                                DIV -> fdiv()
                            }
                            currStack--
                            ret()
                        }
                    }

                    is Value -> {
                        val value = node.value
                        if (value.toRawBits() == 0 || value == 1.0f || value == 2.0f) {
                            fconst(value.toInt())
                        } else {
                            ldc(float(value))
                        }
                        currStack++
                        maxStack = max(maxStack, currStack)
                        ret()
                    }

                    is Var -> {
                        fload(n2i[node.name]!! + 2)
                        currStack++
                        maxStack = max(maxStack, currStack)
                        ret()
                    }
                }
            }

            maxStack(maxStack)
            freturn()
        }
    }
}