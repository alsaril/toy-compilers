package com.alsaril.scheme.runtime

import java.util.concurrent.ConcurrentHashMap

data class Cons(val first: Any, val second: Any) {
    companion object {
        @JvmStatic
        fun of(first: Any, second: Any) = Cons(first, second)
    }
}

data object Nil
class Symbol private constructor(val name: String) {
    companion object {
        private val symbols = ConcurrentHashMap<String, Symbol>()

        fun of(name: String): Symbol = symbols.computeIfAbsent(name, ::Symbol)
    }
}

object Unspecified

class Box(@JvmField var value: Any?) {
    constructor() : this(null)
}