package com.alsaril.math

import com.alsaril.codegen.classfile.ClassFileBuilder
import com.alsaril.codegen.classfile.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.MethodAccessFlag.FINAL
import com.alsaril.codegen.classfile.MethodAccessFlag.PUBLIC
import com.alsaril.codegen.classfile.code.*
import com.alsaril.math.BinaryKind.*
import kotlin.math.abs
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
        fun visit(node: Node) {
            when (node) {
                is Op -> {
                    visit(node.left)
                    visit(node.right)
                }

                is Value -> {}
                is Var -> n2i.putIfAbsent(node.name, n2i.size)
            }
        }
        visit(ast)
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
                if (abs(value) == 0.0f || value == 1.0f || value == 2.0f) {
                    fconst(value.toInt())
                } else {
                    ldc(float(value))
                }
            }

            var depth = 0

            fun walk(node: Node, before: Int): Int {
                return when (node) {
                    is Value -> {
                        value(node.value)
                        depth = max(depth, before + 1)
                        before + 1
                    }

                    is Var -> {
                        fload(n2i[node.name]!! + 2)
                        depth = max(depth, before + 2)
                        before + 1
                    }

                    is Op -> {
                        val ld = walk(node.left, before)
                        depth = max(depth, ld)
                        val rd = walk(node.right, ld)
                        depth = max(depth, rd)
                        when (node.kind) {
                            ADD -> fadd()
                            SUB -> fsub()
                            MUL -> fmul()
                            DIV -> fdiv()
                        }
                        rd - 1
                    }
                }
            }

            walk(ast, 0)
            maxStack(depth)
            freturn()
        }
    }
}