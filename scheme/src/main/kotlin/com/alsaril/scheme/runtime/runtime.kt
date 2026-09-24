package com.alsaril.scheme.runtime

data class Cons(val first: Any, val second: Any) {
    companion object {
        @JvmStatic
        fun of(first: Any, second: Any) = Cons(first, second)
    }
}

data object Nil
data class Symbol(val name: String)