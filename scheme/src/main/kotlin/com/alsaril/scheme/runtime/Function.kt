package com.alsaril.scheme.runtime

import com.alsaril.scheme.SchemeRuntimeException

const val INLINE_FUNCTION_MAX_ARITY = 5

private fun ensureArity(arity: Int) =
    if (arity > INLINE_FUNCTION_MAX_ARITY) throw IllegalArgumentException("should not happen")
    else arity

fun internalName(arity: Int) = "call${ensureArity(arity)}"

fun internalDescriptor(arity: Int) = "(" + "Ljava/lang/Object;".repeat(ensureArity(arity)) + ")Ljava/lang/Object;"

interface Function {
    fun arity(): Int
    fun call0(): Any = throw got(0)
    fun call1(arg1: Any): Any = throw got(1)
    fun call2(arg1: Any, arg2: Any): Any = throw got(2)
    fun call3(arg1: Any, arg2: Any, arg3: Any): Any = throw got(3)
    fun call4(arg1: Any, arg2: Any, arg3: Any, arg4: Any): Any = throw got(4)
    fun call5(arg1: Any, arg2: Any, arg3: Any, arg4: Any, arg5: Any): Any = throw got(4)

    private inline fun got(args: Int) = SchemeRuntimeException("expected ${arity()} args but got $args")
}

// todo add static { check consistency of function and INLINE_FUNCTION_MAX_ARITY }