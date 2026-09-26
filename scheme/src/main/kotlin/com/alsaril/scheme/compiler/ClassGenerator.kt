package com.alsaril.scheme.compiler

import com.alsaril.codegen.classfile.AccessFlag.FINAL
import com.alsaril.codegen.classfile.AccessFlag.PUBLIC
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

    private fun CodeBuilder.resolveSymbol(name: String) {
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

    private fun CodeBuilder.rawSymbol(name: String) {
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

    private fun CodeBuilder.boolean(value: Boolean) =
        +getstatic(
            field(
                clazz("java/lang/Boolean"),
                if (value) "TRUE" else "FALSE",
                "Ljava/lang/Boolean;"
            )
        )

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

    private fun CodeBuilder.pair() {
        +invokestatic(
            smethod(
                clazz("com/alsaril/scheme/runtime/Cons"),
                "of",
                "(Ljava/lang/Object;Ljava/lang/Object;)Lcom/alsaril/scheme/runtime/Cons;"
            )
        )
    }

    private fun CodeBuilder.boolTemplate(args: Node, identity: Boolean) {
        var i = args
        val l = mutableListOf<Node>()
        while (i is Cell) {
            l.add(i.first)
            i = i.second
        }
        require(i is Null)
        if (l.isEmpty()) {
            boolean(identity)
            return
        }
        if (l.size == 1) {
            list(l.first(), resolve = true, exec = true)
            return
        }
        val labels = l.asSequence()
            .take(l.size - 1)
            .map {
                list(it, resolve = true, exec = true)
                boolean(!identity)
                +if_acmpeq
            }
            .toList()

        val exit = l.last().let {
            list(it, resolve = true, exec = true)
            +goto
        }

        val fail = boolean(!identity)
        labels.forEach { link(it, fail) }
        link(exit, end())
    }

    private fun CodeBuilder.special(name: String, args: Node): Boolean {
        if (name == "quote") {
            require(args is Cell && args.second is Null)
            list(args.first, resolve = false, exec = false)
            return true
        }

        if (name == "and") {
            boolTemplate(args, true)
            return true
        }

        if (name == "or") {
            boolTemplate(args, false)
            return true
        }

        return false
    }

    private fun CodeBuilder.call(cell: Cell) {
        val (op, args) = cell
        require(op is Symbol)
        if (special(op.name, args)) return
        resolveSymbol(op.name)
        +checkcast(clazz("com/alsaril/scheme/runtime/Function"))
        list(args, resolve = true, exec = false)
        +invokeinterface(
            imethod(
                clazz("com/alsaril/scheme/runtime/Function"),
                "call",
                "(Ljava/lang/Object;)Ljava/lang/Object;"
            )
        )
    }

    private fun CodeBuilder.list(node: Node, resolve: Boolean, exec: Boolean) {
        if (node is Cell) {
            if (exec) {
                call(node)
            } else {
                val (first, second) = node
                list(first, resolve = resolve, exec = resolve)
                list(second, resolve = resolve, exec = false)
                pair()
            }
            return
        }
        when (node) {
            is Null -> `null`()
            is Number -> number(node.value)
            is Symbol -> when (node.name) {
                "#f" -> boolean(false)
                "#t" -> boolean(true)
                else -> if (resolve) resolveSymbol(node.name) else rawSymbol(node.name)
            }
        }
    }

    private fun ClassFileBuilder.generateProcedure(node: Node) =
        method("run", "(Lcom/alsaril/scheme/runtime/Context;)Ljava/lang/Object;", PUBLIC, FINAL) {
            list(node, resolve = true, exec = true)
            +areturn
        }
}
