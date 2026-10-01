package com.alsaril.scheme.runtime

data class Cons(val first: Any, val second: Any) {
    companion object {
        @JvmStatic
        fun of(first: Any, second: Any) = Cons(first, second)
    }
}

data object Nil
data class Symbol(val name: String)

object Unspecified

internal fun argumentList(args: Any): List<Any> {
    val result = mutableListOf<Any>()
    var rest = args
    while (rest is Cons) {
        result.add(rest.first)
        rest = rest.second
    }
    check(rest == Nil) { "arguments are not a proper list: ${Printer.print(args)}" }
    return result
}

internal fun arguments(count: Int) = if (count == 1) "1 argument" else "$count arguments"
