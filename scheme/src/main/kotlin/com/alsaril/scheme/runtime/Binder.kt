package com.alsaril.scheme.runtime

import com.alsaril.scheme.SchemeRuntimeException

object Binder {
    @JvmStatic
    fun bind(args: Any, names: List<String>, rest: String?, environment: Environment): Environment {
        val local = LocalEnvironment(environment)
        var arg = args
        for (name in names) {
            if (arg !is Cons) throw arity(args, names, rest)
            local.define(name, arg.first)
            arg = arg.second
        }
        if (rest == null) {
            if (arg != Nil) throw arity(args, names, rest)
        } else {
            local.define(rest, arg)
        }
        return local
    }

    private fun arity(args: Any, names: List<String>, rest: String?): SchemeRuntimeException {
        val parameters = names.joinToString(" ", "(", if (rest == null) ")" else " . $rest)")
        val expected = if (rest == null) arguments(names.size) else "at least ${arguments(names.size)}"
        return SchemeRuntimeException("procedure $parameters: expected $expected, got ${argumentList(args).size}")
    }
}
