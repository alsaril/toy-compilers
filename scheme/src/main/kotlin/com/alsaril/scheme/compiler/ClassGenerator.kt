package com.alsaril.scheme.compiler

import com.alsaril.codegen.ClassOutput
import com.alsaril.codegen.classfile.AccessFlag.*
import com.alsaril.codegen.code.*
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.INVOKE_STATIC
import com.alsaril.codegen.instruction.*
import com.alsaril.scheme.SchemeSyntaxException
import com.alsaril.scheme.parser.*
import com.alsaril.scheme.parser.Number
import com.alsaril.scheme.runtime.Cons
import com.alsaril.scheme.runtime.Nil
import java.lang.invoke.MethodHandles
import kotlin.LazyThreadSafetyMode.NONE

class ClassGenerator private constructor() {
    companion object {
        internal val lookup: MethodHandles.Lookup = MethodHandles.lookup()

        fun generate(node: Node) = ClassGenerator().generate(node)
    }

    private var lambdaCnt = 0

    private val classFileBuilder = classFile("com/alsaril/scheme/compiler/Impl", parent = "java/lang/Object")
        .iface("com/alsaril/scheme/runtime/Program")
        .method("<init>", "()V", PUBLIC) {
            +aload(0)
            +invokespecial(parent(), "<init>", "()V")
            +`return`
        }

    private val classData = mutableListOf<Any>()

    private fun generate(node: Node) = ClassOutput(
        generateProcedure(node).build(),
        classData.takeIf { it.isNotEmpty() }?.toList()
    )

    private fun CodeBuilder.resolveSymbol(name: String) {
        +aload(1)
        +ldc(string(name))
        +invokeinterface(
            clazz("com/alsaril/scheme/runtime/Environment"),
            "resolve",
            "(Ljava/lang/String;)Ljava/lang/Object;"
        )
    }

    private fun CodeBuilder.nil() {
        +getstatic(field(clazz("com/alsaril/scheme/runtime/Nil"), "INSTANCE", "Lcom/alsaril/scheme/runtime/Nil;"))
    }

    private fun CodeBuilder.unspecified() {
        +getstatic(
            field(
                clazz("com/alsaril/scheme/runtime/Unspecified"),
                "INSTANCE",
                "Lcom/alsaril/scheme/runtime/Unspecified;"
            )
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
        +invokestatic(clazz("java/lang/Integer"), "valueOf", "(I)Ljava/lang/Integer;")
    }

    private fun CodeBuilder.pair() {
        +invokestatic(
            clazz("com/alsaril/scheme/runtime/Cons"),
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;)Lcom/alsaril/scheme/runtime/Cons;"
        )
    }

    private fun syntaxError(form: Node, problem: String) = SchemeSyntaxException("$problem in ${form.source()}")

    private fun operands(form: Cell): List<Node> {
        val (list, rest) = collectImproperArgs(form.second)
        if (rest != null) throw syntaxError(form, "expected a proper list of operands")
        return list
    }

    private fun collectImproperArgs(args: Node): Pair<List<Node>, Node?> {
        var i = args
        val result = mutableListOf<Node>()
        while (i is Cell) {
            result.add(i.first)
            i = i.second
        }
        return result to (if (i is Null) null else i)
    }

    private fun CodeBuilder.boolTemplate(l: List<Node>, identity: Boolean) {
        if (l.isEmpty()) {
            boolean(identity)
            return
        }
        val exits = l.dropLast(1).map {
            eval(it)
            +dup
            boolean(false)
            val exit = if (identity) +if_acmpeq else +if_acmpne
            +pop
            exit
        }
        eval(l.last())
        exits.forEach { link(it, end()) }
    }

    private fun datum(node: Node): Any {
        if (node is Cell) {
            return Cons.of(datum(node.first), datum(node.second))
        }
        return when (node) {
            is Null -> Nil
            is Number -> node.value
            is Symbol -> com.alsaril.scheme.runtime.Symbol.of(node.name)
            is Special -> when (node.name) {
                "#f" -> false
                "#t" -> true
                else -> com.alsaril.scheme.runtime.Symbol.of(node.name)
            }
        }
    }

    private fun CodeBuilder.special(form: Cell): Boolean {
        val name = (form.first as? Special)?.name ?: return false
        val args = form.second
        val operands by lazy(NONE) { operands(form) }

        if (name == "quote") {
            if (operands.size != 1) throw syntaxError(form, "quote: expected 1 operand, got ${operands.size}")
            val index = classData.size
            classData.add(datum(operands[0]))
            val classDataAt = methodHandle(
                INVOKE_STATIC,
                clazz("java/lang/invoke/MethodHandles"),
                "classDataAt",
                "(${CBP}I)Ljava/lang/Object;"
            )
            +ldc(constantDynamic("_", "Ljava/lang/Object;", bootstrap(classDataAt, int(index))))
            return true
        }

        if (name == "and") {
            boolTemplate(operands, true)
            return true
        }

        if (name == "or") {
            boolTemplate(operands, false)
            return true
        }

        if (name == "define" && args is Cell && args.first is Cell) { // this is a lambda
            val head = args.first
            val procedure = head.first as? Symbol
                ?: throw syntaxError(form, "define: expected a symbol as the procedure name, got ${head.first.source()}")
            bind(procedure.name, Binding.DEFINE) {
                lambda(name, form, listOf(head.second) + operands.drop(1))
            }
            return true
        }

        if (name == "define" || name == "set!") {
            if (operands.size != 2) throw syntaxError(form, "$name: expected 2 operands, got ${operands.size}")
            val (key, def) = operands
            if (key !is Symbol) throw syntaxError(form, "$name: expected a symbol, got ${key.source()}")
            val binding = if (name == "define") Binding.DEFINE else Binding.SET
            bind(key.name, binding) { eval(def) }
            return true
        }

        if (name == "if") {
            if (operands.size !in 2..3) throw syntaxError(form, "if: expected 2 or 3 operands, got ${operands.size}")
            eval(operands[0])
            boolean(false)
            val f = +if_acmpeq
            eval(operands[1])
            val end = +goto
            link(f, end())
            if (operands.size == 2) unspecified() else eval(operands[2])
            link(end, end())

            return true
        }

        if (name == "lambda") {
            lambda(name, form, operands)
            return true
        }

        return false
    }

    private enum class Binding(val method: String) { DEFINE("define"), SET("set") }

    private fun CodeBuilder.bind(name: String, binding: Binding, value: () -> Unit) {
        +aload(1)
        +ldc(string(name))
        value()
        +invokeinterface(
            clazz("com/alsaril/scheme/runtime/Environment"),
            binding.method,
            "(Ljava/lang/String;Ljava/lang/Object;)V"
        )
        unspecified()
    }

    private fun CodeBuilder.lambda(keyword: String, form: Cell, l: List<Node>) {
        if (l.isEmpty()) throw syntaxError(form, "$keyword: expected parameters and a body")
        if (l.size == 1) throw syntaxError(form, "$keyword: expected a body")

        val head = l.first()
        val nodes = l.drop(1)
        val (args, rest) = collectImproperArgs(head)
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

        val lambda = generateLambda(argNames, restName, nodes)
        val bootstrap = lambdaBootstrap(
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            methodHandle(INVOKE_STATIC, self(), lambda, "(Lcom/alsaril/scheme/runtime/Environment;Ljava/lang/Object;)Ljava/lang/Object;"),
            "(Ljava/lang/Object;)Ljava/lang/Object;",
        )
        +aload(1)
        +invokedynamic("call", "(Lcom/alsaril/scheme/runtime/Environment;)Lcom/alsaril/scheme/runtime/Function;", bootstrap)
    }

    private fun CodeBuilder.call(cell: Cell) {
        if (special(cell)) return
        operands(cell) // rejects a dotted argument list
        val (op, args) = cell
        eval(op)
        +invokestatic(
            clazz("com/alsaril/scheme/runtime/Procedures"),
            "procedure",
            "(Ljava/lang/Object;)Lcom/alsaril/scheme/runtime/Function;"
        )
        copyArgs(args)
        +invokeinterface(
            clazz("com/alsaril/scheme/runtime/Function"),
            "call",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        )
    }

    private fun CodeBuilder.eval(node: Node) {
        if (node is Cell) {
            call(node)
            return
        }
        when (node) {
            is Null -> throw SchemeSyntaxException("() is not an expression, quote it as '() for the empty list")
            is Number -> number(node.value)
            is Symbol -> resolveSymbol(node.name)
            is Special -> when (node.name) {
                "#f" -> boolean(false)
                "#t" -> boolean(true)
                else -> throw SchemeSyntaxException("${node.name} is a special form, not a value")
            }
        }
    }

    private fun CodeBuilder.copyArgs(node: Node) {
        if (node is Cell) {
            val (first, second) = node
            eval(first)
            copyArgs(second)
            pair()
            return
        }
        nil() // operands() has refused any other tail
    }

    private fun generateProcedure(node: Node) =
        classFileBuilder.method("run", "(Lcom/alsaril/scheme/runtime/Environment;)Ljava/lang/Object;", PUBLIC, FINAL) {
            eval(node)
            +areturn
        }

    private fun generateLambda(
        names: List<String>,
        rest: String?,
        nodes: List<Node>,
    ): String {
        val name = "lambda${lambdaCnt++}"
        classFileBuilder.method(name, "(Lcom/alsaril/scheme/runtime/Environment;Ljava/lang/Object;)Ljava/lang/Object;", PRIVATE, STATIC, FINAL) {
            +aload(1)
            val listOf = methodHandle(
                INVOKE_STATIC,
                clazz("java/util/List"),
                "of",
                "([Ljava/lang/Object;)Ljava/util/List;",
                onInterface = true
            )
            val args = names.map(::string).toTypedArray()
            val invoke = methodHandle(
                INVOKE_STATIC,
                clazz("java/lang/invoke/ConstantBootstraps"),
                "invoke",
                "(${CBP}Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/Object;",
            )
            +ldc(constantDynamic("_", "Ljava/util/List;", bootstrap(invoke, listOf, *args)))
            if (rest != null) {
                +ldc(string(rest))
            } else {
                +aconst_null
            }
            +aload(0)
            +invokestatic(
                clazz("com/alsaril/scheme/runtime/Binder"),
                "bind",
                "(Ljava/lang/Object;Ljava/util/List;Ljava/lang/String;Lcom/alsaril/scheme/runtime/Environment;)Lcom/alsaril/scheme/runtime/Environment;"
            )
            +astore(1)
            +aconst_null // the outer scope stays reachable only through the new environment
            +astore(0)
            nodes.forEachIndexed { index, node ->
                eval(node)
                if (index == nodes.size - 1) {
                    +areturn
                } else {
                    +pop
                }
            }
        }
        return name
    }
}
