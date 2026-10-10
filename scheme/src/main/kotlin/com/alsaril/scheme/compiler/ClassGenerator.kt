package com.alsaril.scheme.compiler

import com.alsaril.codegen.ClassOutput
import com.alsaril.codegen.classfile.AccessFlag.*
import com.alsaril.codegen.code.*
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.INVOKE_STATIC
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.INVOKE_VIRTUAL
import com.alsaril.codegen.debug.ClassDump
import com.alsaril.codegen.instruction.*
import com.alsaril.scheme.analyser.*
import com.alsaril.scheme.analyser.BooleanConstant.FALSE
import com.alsaril.scheme.analyser.BooleanConstant.TRUE
import com.alsaril.scheme.analyser.Reference.Location.*
import com.alsaril.scheme.parser.*
import com.alsaril.scheme.parser.Number
import com.alsaril.scheme.parser.Symbol
import com.alsaril.scheme.runtime.*
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType

class ClassGenerator {
    companion object {
        internal val lookup: MethodHandles.Lookup = MethodHandles.lookup()

        fun generate(expression: GlobalForm) = ClassGenerator().generateBootstrap(expression)
    }

    private var lambdaCnt = 0

    private val classFileBuilder = classFile("com/alsaril/scheme/compiler/Impl", parent = "java/lang/Object");

    private val classData = mutableListOf<Any>()

    private fun generateBootstrap(expression: GlobalForm): ClassOutput {
        val classFile = classFile("com/alsaril/scheme/compiler/Proxy", parent = "java/lang/Object")
            .iface("com/alsaril/scheme/runtime/Program")
            .method("<init>", "()V", PUBLIC) {
                +aload(0)
                +invokespecial(parent(), "<init>", "()V")
                +`return`
            }
            .method("run", "(Lcom/alsaril/scheme/runtime/Environment;)Ljava/lang/Object;", PUBLIC, FINAL) {
                val classData = methodHandle(
                    INVOKE_STATIC,
                    clazz("java/lang/invoke/MethodHandles"),
                    "classData",
                    "(${CBP})Ljava/lang/Object;"
                )
                +ldc(constantDynamic("_", "Ljava/lang/invoke/MethodHandle;", bootstrap(classData)))
                +aload(1)
                +invokevirtual(
                    clazz("java/lang/invoke/MethodHandle"),
                    "invoke",
                    "(Lcom/alsaril/scheme/runtime/Environment;)Ljava/lang/Object;"
                )
                +checkcast(clazz("java/util/function/Supplier"))
                +invokeinterface(clazz("java/util/function/Supplier"), "get", "()Ljava/lang/Object;")
                +areturn
            }
            .build()

        return ClassOutput(classFile, generate(expression))
    }

    private fun generate(expression: GlobalForm): MethodHandle {
        val globalFields = expression.references.filter { it.location == GLOBAL }
        val impl = classFileBuilder
            .iface("java/util/function/Supplier")
            .apply {
                globalFields.forEach { ref ->
                    field(ref.fieldName(), "Lcom/alsaril/scheme/runtime/Box;", PRIVATE, FINAL)
                }
            }
            .method("<init>", "(Lcom/alsaril/scheme/runtime/Environment;)V", PUBLIC) {
                +aload(0)
                +invokespecial(parent(), "<init>", "()V")
                globalFields.forEach { ref ->
                    +aload(0)
                    +aload(1)
                    +ldc(string(ref.name))
                    +invokeinterface(
                        clazz("com/alsaril/scheme/runtime/Environment"),
                        "get",
                        "(Ljava/lang/String;)Lcom/alsaril/scheme/runtime/Box;"
                    )
                    +putfield(field(self(), ref.fieldName(), "Lcom/alsaril/scheme/runtime/Box;"))
                }
                +`return`
            }
            .method("get", "()Ljava/lang/Object;", PUBLIC, FINAL) {
                emitEval(expression.body)
                +areturn
            }
            .build()

        ClassDump.dump(impl)

        val classData = classData.takeIf { it.isNotEmpty() }?.toList()
        val hidden = when (classData) {
            null -> lookup.defineHiddenClass(impl, true)
            else -> lookup.defineHiddenClassWithClassData(impl, classData, true)
        }

        return hidden.findConstructor(hidden.lookupClass(), methodType(Void.TYPE, Environment::class.java))
    }

    private fun CodeBuilder.emitUnspecified() {
        +getstatic(
            field(
                clazz("com/alsaril/scheme/runtime/Unspecified"),
                "INSTANCE",
                "Lcom/alsaril/scheme/runtime/Unspecified;"
            )
        )
    }

    private fun CodeBuilder.emitBoolean(value: Boolean) =
        +getstatic(
            field(
                clazz("java/lang/Boolean"),
                if (value) "TRUE" else "FALSE",
                "Ljava/lang/Boolean;"
            )
        )

    private fun CodeBuilder.emitNumber(value: Int) {
        +ldc(int(value))
        +invokestatic(clazz("java/lang/Integer"), "valueOf", "(I)Ljava/lang/Integer;")
    }

    private fun CodeBuilder.emitUnbox() {
        +getfield(field(clazz("com/alsaril/scheme/runtime/Box"), "value", "Ljava/lang/Object;"))
    }

    private fun CodeBuilder.emitLoad(reference: Reference) {
        when (reference.location) {
            GLOBAL -> {
                +aload(0)
                +getfield(field(self(), reference.fieldName(), "Lcom/alsaril/scheme/runtime/Box;"))
                emitUnbox()
            }

            CAPTURE -> {
                if (reference.boxed) {
                    +getfield(field(self(), reference.fieldName(), "Lcom/alsaril/scheme/runtime/Box;"))
                    emitUnbox()
                } else {
                    +getfield(field(self(), reference.fieldName(), "Ljava/lang/Object;"))
                }
            }

            LOCAL -> {
                +aload(reference.index)
                if (reference.boxed) {
                    emitUnbox()
                }
            }
        }
    }

    private fun CodeBuilder.emitBooleanExpression(expression: BooleanExpression) {
        if (expression.args.isEmpty()) {
            emitBoolean(expression.identity)
            return
        }
        val exits = expression.args.dropLast(1).map {
            emitEval(it)
            +dup
            emitBoolean(false)
            val exit = if (expression.identity) +if_acmpeq else +if_acmpne
            +pop
            exit
        }
        emitEval(expression.args.last())
        exits.forEach { link(it, end()) }
    }

    private fun CodeBuilder.emitCall(call: Call) {
        emitEval(call.function)
        call.args.asSequence().take(INLINE_FUNCTION_MAX_ARITY).forEach { emitEval(it) }
        if (call.args.size > INLINE_FUNCTION_MAX_ARITY) { // variadic tail
            +ldc(int(call.args.size - INLINE_FUNCTION_MAX_ARITY))
            +anewarray(clazz("java/lang/Object"))
            call.args.asSequence().drop(INLINE_FUNCTION_MAX_ARITY).forEachIndexed { it, arg ->
                +dup
                +ldc(int(it))
                emitEval(arg)
                +aaload
            }
        }
        +invokeinterface(
            clazz("com/alsaril/scheme/runtime/Function"),
            internalName(call.args.size),
            internalDescriptor(call.args.size),
        )
        // todo revive tail calls
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

    private fun CodeBuilder.emitDatum(datum: Datum) {
        val index = classData.size
        classData.add(datum(datum.value))
        val classDataAt = methodHandle(
            INVOKE_STATIC,
            clazz("java/lang/invoke/MethodHandles"),
            "classDataAt",
            "(${CBP}I)Ljava/lang/Object;"
        )
        +ldc(constantDynamic("_", "Ljava/lang/Object;", bootstrap(classDataAt, int(index))))
    }

    private fun CodeBuilder.emitBox() {
        +putfield(field(clazz("com/alsaril/scheme/runtime/Box"), "value", "Ljava/lang/Object;"))
    }

    private fun CodeBuilder.emitBind(bind: Bind) {
        when (bind.reference.location) {
            GLOBAL -> {
                +aload(0)
                +getfield(field(self(), bind.reference.fieldName(), "Lcom/alsaril/scheme/runtime/Box;"))
                emitEval(bind.value)
                emitBox()
            }

            CAPTURE -> {
                +getfield(field(self(), bind.reference.fieldName(), "Lcom/alsaril/scheme/runtime/Box;"))
                emitEval(bind.value)
                emitBox()
            }

            LOCAL -> {
                if (bind.reference.boxed) {
                    +aload(bind.reference.index)
                    emitEval(bind.value)
                    emitBox()
                } else {
                    emitEval(bind.value)
                    +astore(bind.reference.index)
                }
            }
        }
    }

    private fun CodeBuilder.emitIf(expression: IfExpression) {
        emitEval(expression.condition)
        emitBoolean(false)
        val f = +if_acmpeq
        emitEval(expression.thenBranch)
        val end = +goto
        link(f, end())
        if (expression.elseBranch == null) emitUnspecified() else emitEval(expression.elseBranch)
        link(end, end())
    }

    private fun CodeBuilder.emitLambda(lambda: Lambda) {
//        val captureDescriptor =
//            lambda.frame.captures.joinToString { (_, boxed) -> if (boxed) "Lcom/alsaril/scheme/runtime/Box;" else "Ljava/lang/Object;" }
//        val methodDescriptor =
//            "(" + captureDescriptor + "Ljava/lang/Object;".repeat(lambda.frame.locals.size) + ")Ljava/lang/Object;"
//        val constructorDescriptor = "($captureDescriptor)Lcom/alsaril/scheme/runtime/Function;"
//        val name = emitLambdaMethod(lambda, methodDescriptor)
//        val bootstrap = bootstrap(
//            methodHandle(
//                INVOKE_STATIC,
//                clazz("com/alsaril/scheme/compiler/LambdaBootstrap"),
//                "bootstrap",
//                "(${IDP}Ljava/lang/invoke/MethodHandle;I)Ljava/lang/invoke/CallSite;"
//            ),
//            methodHandle(INVOKE_VIRTUAL, self(), name, methodDescriptor),
//        )
//        +invokedynamic("_", constructorDescriptor, bootstrap)
    }

    private fun emitLambdaMethod(lambda: Lambda, descriptor: String): String {
        val name = "lambda${lambdaCnt++}"
        classFileBuilder.method(name, descriptor, PRIVATE, STATIC, FINAL) {
            // wrap volatile locals
            lambda.references
                .asSequence()
                .filter { it.location == LOCAL && it.boxed }
                .forEach { ref ->
                    +new(clazz("com/alsaril/scheme/runtime/Box"))
                    +dup
                    +aload(ref.index)
                    +invokespecial(clazz("com/alsaril/scheme/runtime/Box"), "<init>", "()V")
                    +astore(ref.index)
                }
            lambda.body.forEachIndexed { index, node ->
                if (index == lambda.body.size - 1) {
                    emitEval(node)
                    +areturn
                } else {
                    emitEval(node)
                    +pop
                }
            }
        }
        return name
    }

    private fun CodeBuilder.emitEval(expression: Expression) {
        when (expression) {
            FALSE -> emitBoolean(false)
            TRUE -> emitBoolean(true)
            is NumberConstant -> emitNumber(expression.value)
            is Reference -> emitLoad(expression)
            is BooleanExpression -> emitBooleanExpression(expression)
            is Call -> emitCall(expression)
            is Datum -> emitDatum(expression)
            is Bind -> emitBind(expression)
            is IfExpression -> emitIf(expression)
            is Lambda -> emitLambda(expression)
            is GlobalForm -> throw IllegalStateException()
        }
    }
}