package com.alsaril.scheme.runtime

object Printer {
    fun print(value: Any): String = when (value) {
        is Boolean -> if (value) "#t" else "#f"
        is Int -> value.toString()
        is Symbol -> value.name
        is Cons -> {
            val result = mutableListOf<String>()
            var i = value
            while (i is Cons) {
                val (f, s) = i
                result.add(print(f))
                i = s
            }
            val sb = StringBuilder("(")
            result.forEach { sb.append(it).append(" ") }
            if (i != Nil) {
                sb.append(". ").append(print(i))
            } else {
                sb.deleteCharAt(sb.length - 1)
            }
            sb.append(")").toString()
        }

        else -> throw IllegalArgumentException(value.toString())
    }
}