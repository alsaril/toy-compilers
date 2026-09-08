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

    private fun ClassFileBuilder.generateEval(ast: Node) = apply {
        method("eval", "(Ljava/util/Map;)F", maxStack = -1, maxLocals = 2, PUBLIC, FINAL) {
            fun value(value: Float) {
                if (value == 0.0f || value == 1.0f || value == 2.0f) {
                    fconst(value.toInt())
                } else {
                    ldc(float(value))
                }
            }

            fun variable(name: String) {
                aload(1)
                ldc(string(name))
                invokeinterface(imethod(clazz("java/util/Map"), "get", "(Ljava/lang/Object;)Ljava/lang/Object;"), 2)
                checkcast(clazz("java/lang/Float"))
                invokevirtual(method(clazz("java/lang/Float"), "floatValue", "()F"))
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
                        variable(node.name)
                        depth = max(depth, before + 1)
                        before + 1
                    }
                    is Op ->  {
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