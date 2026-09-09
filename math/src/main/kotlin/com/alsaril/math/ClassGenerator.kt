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

            fun value(value: Float) {
                if (value.toRawBits() == 0 || value == 1.0f || value == 2.0f) {
                    fconst(value.toInt())
                } else {
                    ldc(float(value))
                }
            }

            fun walk(node: Node, before: Int, max: Int): Pair<Int, Int>  /*height, max */ {
                return when (node) {
                    is Value -> {
                        value(node.value)
                        (before + 1) to max(max, before + 1)
                    }

                    is Var -> {
                        fload(n2i[node.name]!! + 2)
                        (before + 1) to max(max, before + 1)
                    }

                    is Op -> {
                        val (hl, maxL) = walk(node.left, before, max)
                        val (hr, maxR) = walk(node.right, hl, maxL)
                        when (node.kind) {
                            ADD -> fadd()
                            SUB -> fsub()
                            MUL -> fmul()
                            DIV -> fdiv()
                        }
                        (hr - 1) to maxR
                    }

                    is Neg -> {
                        val (h, maxA) = walk(node.arg, before, max)
                        fneg()
                        h to max(max, maxA)
                    }
                }
            }

            val (_, max) = walk(ast, 0, 0)
            maxStack(max)
            freturn()
        }
    }
}