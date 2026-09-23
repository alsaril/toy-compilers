package com.alsaril.scheme

interface Context {
    fun register(name: String, value: Any)
    fun resolve(name: String): Any
}