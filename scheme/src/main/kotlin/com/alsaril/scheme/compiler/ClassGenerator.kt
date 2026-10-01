package com.alsaril.scheme.compiler

import com.alsaril.codegen.ClassDef
import com.alsaril.codegen.ClassGraph
import com.alsaril.codegen.classfile.AccessFlag.*
import com.alsaril.codegen.code.*
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.INVOKE_STATIC
import com.alsaril.codegen.instruction.*
import com.alsaril.scheme.SchemeSyntaxException
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

    private fun CodeBuilder.unspecified() {
        +getstatic(
            field(clazz("com/alsaril/scheme/runtime/Unspecified"), "INSTANCE", "Lcom/alsaril/scheme/runtime/Unspecified;")
        )
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

    private fun syntaxError(form: Node, problem: String) = SchemeSyntaxException("$problem in ${form.source()}")

    private fun operands(form: Cell): List<Node> {
        val (list, rest) = collectUnproperArgs(form.second)
        if (rest != null) throw syntaxError(form, "expected a proper list of operands")
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

    private fun CodeBuilder.special(form: Cell, context: Context): Boolean {
        val name = (form.first as? Symbol)?.name ?: return false
        val args = form.second
        val l = lazy(NONE) { operands(form) }

        if (name == "quote") {
            if (l.value.size != 1) throw syntaxError(form, "quote: expected 1 operand, got ${l.value.size}")
            list(l.value[0], resolve = false, exec = false, context)
            return true
        }

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
            val procedure = head.first as? Symbol
                ?: throw syntaxError(form, "define: expected a symbol as the procedure name, got ${head.first.source()}")
            bind(procedure.name, Binding.DEFINE) {
                lambda(name, form, listOf(head.second) + l.value.drop(1), context)
            }
            return true
        }

        if (name == "define" || name == "set!") {
            if (l.value.size != 2) throw syntaxError(form, "$name: expected 2 operands, got ${l.value.size}")
            val (key, def) = l.value
            if (key !is Symbol) throw syntaxError(form, "$name: expected a symbol, got ${key.source()}")
            val binding = if (name == "define") Binding.DEFINE else Binding.SET
            bind(key.name, binding) { list(def, resolve = true, exec = true, context) }
            return true
        }

        if (name == "if") {
            if (l.value.size !in 2..3) throw syntaxError(form, "if: expected 2 or 3 operands, got ${l.value.size}")
            list(l.value[0], resolve = true, exec = true, context)
            boolean(false)
            val f = +if_acmpeq
            list(l.value[1], resolve = true, exec = true, context)
            val end = +goto
            link(f, end())
            if (l.value.size == 2) unspecified() else list(l.value[2], resolve = true, exec = true, context)
            link(end, end())

            return true
        }

        if (name == "lambda") {
            lambda(name, form, l.value, context)
            return true
        }

        return false
    }

    private enum class Binding(val method: String) { DEFINE("define"), SET("set") }

    private fun CodeBuilder.bind(name: String, binding: Binding, value: () -> Unit) {
        +aload(1)
        +ldc(string(name))
        value()
        invokeinterface(
            clazz("com/alsaril/scheme/runtime/Environment"),
            binding.method,
            "(Ljava/lang/String;Ljava/lang/Object;)V"
        )
        unspecified()
    }

    private fun CodeBuilder.lambda(keyword: String, form: Cell, l: List<Node>, context: Context) {
        if (l.isEmpty()) throw syntaxError(form, "$keyword: expected parameters and a body")
        if (l.size == 1) throw syntaxError(form, "$keyword: expected a body")

        val head = l.first()
        val nodes = l.drop(1)
        val (args, rest) = collectUnproperArgs(head)
        val argNames = args.map {
            (it as? Symbol)?.name ?: throw syntaxError(form, "$keyword: expected a symbol as a parameter, got ${it.source()}")
        }
        val restName = when (rest) {
            null -> null
            is Symbol -> rest.name
            head -> throw syntaxError(form, "$keyword: expected a parameter list, got ${head.source()}")
            else -> throw syntaxError(form, "$keyword: expected a symbol as the rest parameter, got ${rest.source()}")
        }
        (argNames + listOfNotNull(restName)).groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.let {
            throw syntaxError(form, "$keyword: parameter ${it.key} is declared more than once")
        }

        val lambda = generateLambda(argNames, restName, nodes, context)
        context.addClass(lambda)

        +new(clazz(lambda.first))
        +dup
        +aload(1)
        invokespecial(clazz(lambda.first), "<init>", "(Lcom/alsaril/scheme/runtime/Environment;)V")
    }

    private fun CodeBuilder.call(cell: Cell, context: Context) {
        if (special(cell, context)) return
        operands(cell) // rejects a dotted argument list
        val (op, args) = cell
        list(op, resolve = true, exec = true, context)
        invokestatic(
            clazz("com/alsaril/scheme/runtime/Procedures"),
            "procedure",
            "(Ljava/lang/Object;)Lcom/alsaril/scheme/runtime/Function;"
        )
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
                if (exec) throw SchemeSyntaxException("() is not an expression, quote it as '() for the empty list")
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
