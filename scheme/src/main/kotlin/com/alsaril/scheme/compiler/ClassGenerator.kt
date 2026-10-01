package com.alsaril.scheme.compiler

import com.alsaril.codegen.ClassDef
import com.alsaril.codegen.ClassGraph
import com.alsaril.codegen.classfile.AccessFlag.*
import com.alsaril.codegen.code.*
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.INVOKE_STATIC
import com.alsaril.codegen.instruction.*
import com.alsaril.scheme.parser.*
import com.alsaril.scheme.parser.Number
import kotlin.LazyThreadSafetyMode.NONE

object ClassGenerator {
    private var implCnt = 0
    private var lambdaCnt = 0

    private class Context {
        private val classes = mutableListOf<ClassDef>()

        fun addClass(classDef: ClassDef) {
            classes.add(classDef)
        }

        fun classes(): List<ClassDef> = classes
    }

    fun generate(node: Node): ClassGraph {
        val context = Context()

        val root = classFile("Impl${implCnt++}", parent = "java/lang/Object")
            .iface("com/alsaril/scheme/runtime/Program")
            .method("<init>", "()V", PUBLIC) {
                +aload(0)
                invokespecial(parent(), "<init>", "()V")
                +`return`
            }
            .generateProcedure(node, context)
            .build()

        return ClassGraph(root, context.classes())
    }

    private fun generateLambda(
        args: List<String>,
        rest: String?,
        nodes: List<Node>,
        context: Context,
    ): Pair<String, ByteArray> =
        classFile("Lambda${lambdaCnt++}", parent = "java/lang/Object")
            .iface("com/alsaril/scheme/runtime/Function")
            .field("scope", "Lcom/alsaril/scheme/runtime/Environment;", PRIVATE, FINAL)
            .method("<init>", "(Lcom/alsaril/scheme/runtime/Environment;)V", PUBLIC) {
                +aload(0)
                invokespecial(parent(), "<init>", "()V")
                +aload(0)
                +aload(1)
                +putfield(field(self(), "scope", "Lcom/alsaril/scheme/runtime/Environment;"))
                +`return`
            }
            .generateLambdaBody(args, rest, nodes, context)
            .build()

    private fun CodeBuilder.resolveSymbol(name: String) {
        +aload(1)
        +ldc(string(name))
        invokeinterface(
            clazz("com/alsaril/scheme/runtime/Environment"),
            "resolve",
            "(Ljava/lang/String;)Ljava/lang/Object;"
        )
    }

    private fun CodeBuilder.rawSymbol(name: String) {
        +aload(1)
        +ldc(string(name))
        invokeinterface(
            clazz("com/alsaril/scheme/runtime/Environment"),
            "intern",
            "(Ljava/lang/String;)Lcom/alsaril/scheme/runtime/Symbol;"
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
        invokestatic(clazz("java/lang/Integer"), "valueOf", "(I)Ljava/lang/Integer;")
    }

    private fun CodeBuilder.pair() {
        invokestatic(
            clazz("com/alsaril/scheme/runtime/Cons"),
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;)Lcom/alsaril/scheme/runtime/Cons;"
        )
    }

    private fun collectArgs(args: Node): List<Node> {
        val (list, rest) = collectUnproperArgs(args)
        require(rest == null)
        return list
    }

    private fun collectUnproperArgs(args: Node): Pair<List<Node>, Node?> {
        var i = args
        val result = mutableListOf<Node>()
        while (i is Cell) {
            result.add(i.first)
            i = i.second
        }
        return result to (if (i is Null) null else i)
    }

    private fun CodeBuilder.boolTemplate(l: List<Node>, identity: Boolean, context: Context) {
        if (l.isEmpty()) {
            boolean(identity)
            return
        }
        val exits = l.dropLast(1).map {
            list(it, resolve = true, exec = true, context)
            +dup
            boolean(false)
            val exit = if (identity) +if_acmpeq else +if_acmpne
            +pop
            exit
        }
        list(l.last(), resolve = true, exec = true, context)
        exits.forEach { link(it, end()) }
    }

    private fun CodeBuilder.special(name: String, args: Node, context: Context): Boolean {
        if (name == "quote") {
            require(args is Cell && args.second is Null)
            list(args.first, resolve = false, exec = false, context)
            return true
        }

        val l = lazy(NONE) { collectArgs(args) }

        if (name == "and") {
            boolTemplate(l.value, true, context)
            return true
        }

        if (name == "or") {
            boolTemplate(l.value, false, context)
            return true
        }

        if (name == "define" && args is Cell && args.first is Cell) { // this is a lambda
            val head = args.first
            require(head.first is Symbol)
            val name = head.first.name
            define(name, op = "define") {
                lambda(collectArgs(Cell(head.second, args.second)), context)
            }
            return true
        }

        if (name == "define" || name == "set!") {
            require(l.value.size == 2)
            val (key, def) = l.value
            require(key is Symbol)
            define(key.name, op = name) { list(def, resolve = true, exec = true, context) }
            return true
        }

        if (name == "if") {
            require(l.value.size == 2 || l.value.size == 3)
            list(l.value[0], resolve = true, exec = true, context)
            boolean(false)
            val f = +if_acmpeq
            list(l.value[1], resolve = true, exec = true, context)
            val end = +goto
            link(f, end())
            if (l.value.size == 2) {
                +getstatic(
                    field(
                        clazz("com/alsaril/scheme/runtime/Unspecified"),
                        "INSTANCE",
                        "Lcom/alsaril/scheme/runtime/Unspecified;"
                    )
                )
            } else list(l.value[2], resolve = true, exec = true, context)
            link(end, end())

            return true
        }

        if (name == "lambda") {
            lambda(l.value, context)
            return true
        }

        return false
    }

    private fun CodeBuilder.define(name: String, op: String, def: () -> Unit) {
        +aload(1)
        +ldc(string(name))
        def()
        invokeinterface(
            clazz("com/alsaril/scheme/runtime/Environment"),
            if (op == "define") "define" else "set",
            "(Ljava/lang/String;Ljava/lang/Object;)V"
        )
        +getstatic(
            field(
                clazz("com/alsaril/scheme/runtime/Unspecified"),
                "INSTANCE",
                "Lcom/alsaril/scheme/runtime/Unspecified;"
            )
        )
    }

    private fun CodeBuilder.lambda(l: List<Node>, context: Context) {
        require(l.size >= 2)

        val head = l.first()
        val nodes = l.drop(1)
        val (args, rest) = collectUnproperArgs(head)
        val argNames = args.map {
            it as Symbol
            it.name
        }
        require(rest is Symbol?)
        require(argNames.size == argNames.toSet().size)
        require(rest?.name !in argNames)

        val lambda = generateLambda(argNames, rest?.name, nodes, context)
        context.addClass(lambda)

        +new(clazz(lambda.first))
        +dup
        +aload(1)
        invokespecial(clazz(lambda.first), "<init>", "(Lcom/alsaril/scheme/runtime/Environment;)V")
    }

    private fun CodeBuilder.call(cell: Cell, context: Context) {
        val (op, args) = cell
        if (op is Symbol && special(op.name, args, context)) return
        list(op, resolve = true, exec = true, context)
        +checkcast(clazz("com/alsaril/scheme/runtime/Function"))
        list(args, resolve = true, exec = false, context)
        invokeinterface(
            clazz("com/alsaril/scheme/runtime/Function"),
            "call",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        )
    }

    private fun CodeBuilder.list(node: Node, resolve: Boolean, exec: Boolean, context: Context) {
        if (node is Cell) {
            if (exec) {
                call(node, context)
            } else {
                val (first, second) = node
                list(first, resolve = resolve, exec = resolve, context)
                list(second, resolve = resolve, exec = false, context)
                pair()
            }
            return
        }
        when (node) {
            is Null -> {
                require(!exec)
                `null`()
            }
            is Number -> number(node.value)
            is Symbol -> when (node.name) {
                "#f" -> boolean(false)
                "#t" -> boolean(true)
                else -> if (resolve) resolveSymbol(node.name) else rawSymbol(node.name)
            }
        }
    }

    private fun ClassFileBuilder.generateProcedure(node: Node, context: Context) =
        method("run", "(Lcom/alsaril/scheme/runtime/Environment;)Ljava/lang/Object;", PUBLIC, FINAL) {
            list(node, resolve = true, exec = true, context)
            +areturn
        }

    private fun ClassFileBuilder.generateLambdaBody(
        names: List<String>,
        rest: String?,
        nodes: List<Node>,
        context: Context
    ) =
        method("call", "(Ljava/lang/Object;)Ljava/lang/Object;", PUBLIC, FINAL) {
            +aload(1)
            val listOf = methodHandle(INVOKE_STATIC, clazz("java/util/List"), "of", "([Ljava/lang/Object;)Ljava/util/List;", onInterface = true)
            val args = names.map(::string).toTypedArray()
            +ldc(
                constantDynamic(
                    clazz("java/lang/invoke/ConstantBootstraps"),
                    "invoke",
                    "(${CBP}Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/Object;",
                    listOf,
                    *args,
                    constantType = "Ljava/util/List;",
                )
            )
            if (rest != null) {
                +ldc(string(rest))
            } else {
                +aconst_null
            }
            +aload(0)
            +getfield(field(self(), "scope", "Lcom/alsaril/scheme/runtime/Environment;"))
            invokestatic(
                clazz("com/alsaril/scheme/runtime/Binder"),
                "bind",
                "(Ljava/lang/Object;Ljava/util/List;Ljava/lang/String;Lcom/alsaril/scheme/runtime/Environment;)Lcom/alsaril/scheme/runtime/Environment;"
            )
            +astore(1)
            nodes.forEachIndexed { index, node ->
                list(node, resolve = true, exec = true, context)
                if (index == nodes.size - 1) {
                    +areturn
                } else {
                    +pop
                }
            }
        }
}
