package com.alsaril.scheme.runtime

object Binder {
    @JvmStatic
    fun bind(args: Any, names: List<String>, rest: String?, environment: Environment): Environment {
        val local = LocalEnvironment(environment)
        var i = 0
        var arg = args
        while (i < names.size) {
            require(arg is Cons)
            local.define(names[i], arg.first)
            i++
            arg = arg.second
        }
        if (rest == null) {
            require(arg is Nil)
        } else {
            local.define(rest, arg)
        }
        return local
    }
}