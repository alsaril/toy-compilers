package com.alsaril.scheme.runtime

interface Environment {
    fun define(name: String, value: Any)
    fun set(name: String, value: Any)
    fun resolve(name: String): Any
    fun intern(name: String): Symbol
}