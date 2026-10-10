package com.alsaril.scheme.runtime

interface Environment {
    fun get(name: String): Box
    fun set(name: String, value: Any)
}