package com.alsaril.scheme.runtime

interface Context {
    fun register(name: String, value: Any)
    fun resolve(name: String): Any
}