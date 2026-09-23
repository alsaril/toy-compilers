package com.alsaril.scheme.runtime

import kotlin.Boolean

data class Pair(val first: Any, val second: Any)
data object Null
data class Symbol(val name: String)
enum class Boolean {
    FALSE, TRUE;

    fun not() = if (this == FALSE) TRUE else FALSE

    companion object {
        fun from(b: Boolean) = if (b) TRUE else FALSE
    }
}
data class Number(val value: Int)