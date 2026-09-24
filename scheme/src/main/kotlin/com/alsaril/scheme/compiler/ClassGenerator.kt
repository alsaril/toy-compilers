package com.alsaril.scheme.compiler

import com.alsaril.codegen.classfile.AccessFlag.*
import com.alsaril.codegen.code.*
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.instruction.*
import com.alsaril.scheme.parser.*
import com.alsaril.scheme.parser.Number

object ClassGenerator {
    private var cnt = 0

    fun generate(node: Node): Pair<String, ByteArray> = classFile("Impl${cnt++}", parent = "java/lang/Object")
        .iface("com/alsaril/scheme/runtime/Program")
        .method("<init>", "()V", PUBLIC) {
            +aload(0)
            +invokespecial(method(parent(), "<init>", "()V"))
            +`return`
        }
        .generateProcedure(node)
        .build()

    private fun CodeBuilder.emitResolve(name: String) {
        +aload(1)
        +ldc(string(name))
        +invokeinterface(
            imethod(
                clazz("com/alsaril/scheme/runtime/Context"),
                "resolve",
                "(Ljava/lang/String;)Ljava/lang/Object;"
            )
        )
    }

    private fun CodeBuilder.pair() {
        +invokestatic(
            smethod(
                clazz("com/alsaril/scheme/runtime/Cons"),
                "of",
                "(Ljava/lang/Object;Ljava/lang/Object;)Lcom/alsaril/scheme/runtime/Cons;"
            )
        )
    }

    private fun CodeBuilder.symbol(name: String) {
        +aload(1)
        +ldc(string(name))
        +invokeinterface(
            imethod(
                clazz("com/alsaril/scheme/runtime/Context"),
                "intern",
                "(Ljava/lang/String;)Lcom/alsaril/scheme/runtime/Symbol;"
            )
        )
    }

    private fun CodeBuilder.`null`() {
        +getstatic(field(clazz("com/alsaril/scheme/runtime/Nil"), "INSTANCE", "Lcom/alsaril/scheme/runtime/Nil;"))
    }

    private fun CodeBuilder.number(value: Int) {
        +ldc(int(value))
        +invokestatic(
            smethod(
                clazz("java/lang/Integer"),
                "valueOf",
                "(I)Ljava/lang/Integer;"
            )
        )
    }

    private fun CodeBuilder.boolean(value: Boolean) {
        +getstatic(
            field(
                clazz("java/lang/Boolean"),
                if (value) "TRUE" else "FALSE",
                "Ljava/lang/Boolean;"
            )
        )
    }

    private fun CodeBuilder.emitRaw(node: Node) {
        when (node) {
            is Cell -> {
                val (first, second) = node
                emitRaw(first)
                emitRaw(second)
                pair()
            }

            is Null -> `null`()
            is Number -> number(node.value)
            is Symbol -> when (node.name) {
                "#f" -> boolean(false)
                "#t" -> boolean(true)
                else -> symbol(node.name)
            }
        }
    }

    private fun CodeBuilder.emitTerminal(node: Node) {
        when (node) {
            is Null -> `null`()

            is Number -> number(node.value)

            is Symbol -> when (node.name) {
                "#f" -> boolean(false)
                "#t" -> boolean(true)
                else -> emitResolve(node.name)
            }

            is Cell -> copyArgs(node)
        }
    }

    private fun CodeBuilder.copyArgs(node: Node) {
        when (node) {
            is Null, is Number, is Symbol -> emitTerminal(node)
            is Cell -> {
                val (arg, rest) = node
                when (arg) {
                    is Null, is Number, is Symbol -> emitTerminal(arg)
                    is Cell -> emitEval(arg)
                }
                copyArgs(rest)
                pair()
            }
        }
    }

    private fun CodeBuilder.emitEval(cell: Cell) {
        val (op, args) = cell
        require(op is Symbol)
        if (op.name == "quote") {
            require(args is Cell && args.second is Null)
            emitRaw(args.first)
            return
        }
        emitResolve(op.name)
        +checkcast(clazz("com/alsaril/scheme/runtime/Function"))
        copyArgs(args)
        +invokeinterface(
            imethod(
                clazz("com/alsaril/scheme/runtime/Function"),
                "call",
                "(Ljava/lang/Object;)Ljava/lang/Object;"
            )
        )
    }

    private fun ClassFileBuilder.generateProcedure(node: Node) =
        method("run", "(Lcom/alsaril/scheme/runtime/Context;)Ljava/lang/Object;", PUBLIC, FINAL) {
            when (node) {
                is Symbol -> emitTerminal(node)
                is Cell -> emitEval(node)
                else -> TODO(node.toString())
            }
            +areturn
        }
}
