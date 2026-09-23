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
        .iface("com/alsaril/scheme/Procedure")
        .field("global", "Lcom/alsaril/scheme/Context;", PRIVATE, FINAL)
        .method("<init>", "(Lcom/alsaril/scheme/Context;)V", PUBLIC) {
            +aload(0)
            +dup
            +invokespecial(method(parent(), "<init>", "()V"))
            +aload(1)
            +putfield(field(self(), "global", "Lcom/alsaril/scheme/Context;"))
            +`return`
        }
        .generateProcedure(node)
        .build()

    private fun CodeBuilder.emitResolve(name: String) {
        +aload(0)
        +getfield(field(self(), "global", "Lcom/alsaril/scheme/Context;"))
        +ldc(string(name))
        +invokeinterface(
            imethod(
                clazz("com/alsaril/scheme/Context"),
                "resolve",
                "(Ljava/lang/String;)Ljava/lang/Object;"
            )
        )
    }

    private fun CodeBuilder.pair() {
        +new(clazz("com/alsaril/scheme/runtime/Pair"))
        +dup_x2
        +astore(1)
        +invokespecial(
            method(
                clazz("com/alsaril/scheme/runtime/Pair"),
                "<init>",
                "(Ljava/lang/Object;Ljava/lang/Object;)V"
            )
        )
        +aload(1)
    }

    private fun CodeBuilder.symbol(name: String) {
        +ldc(string(name))
        +new(clazz("com/alsaril/scheme/runtime/Symbol"))
        +dup_x1
        +astore(1)
        +invokespecial(
            method(
                clazz("com/alsaril/scheme/runtime/Symbol"),
                "<init>",
                "(Ljava/lang/String;)V"
            )
        )
        +aload(1)
    }

    private fun CodeBuilder.`null`() {
        +getstatic(field(clazz("com/alsaril/scheme/runtime/Null"), "INSTANCE", "Lcom/alsaril/scheme/runtime/Null;"))
    }

    private fun CodeBuilder.number(value: Int) {
        +ldc(int(value))
        +new(clazz("com/alsaril/scheme/runtime/Number"))
        +dup_x1
        +astore(1)
        +invokespecial(
            method(
                clazz("com/alsaril/scheme/runtime/Number"),
                "<init>",
                "(I)V"
            )
        )
        +aload(1)
    }

    private fun CodeBuilder.boolean(value: Boolean) {
        +getstatic(
            field(
                clazz("com/alsaril/scheme/runtime/Boolean"),
                if (value) "TRUE" else "FALSE",
                "Lcom/alsaril/scheme/runtime/Boolean;"
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
        +checkcast(clazz("com/alsaril/scheme/Function"))
        copyArgs(args)
        +invokeinterface(
            imethod(
                clazz("com/alsaril/scheme/Function"),
                "call",
                "(Ljava/lang/Object;)Ljava/lang/Object;"
            )
        )
    }

    private fun ClassFileBuilder.generateProcedure(node: Node) =
        method("call", "()Ljava/lang/Object;", PUBLIC, FINAL) {
            when (node) {
                is Symbol -> emitTerminal(node)
                is Cell -> emitEval(node)
                else -> TODO(node.toString())
            }
            +areturn
        }
}
