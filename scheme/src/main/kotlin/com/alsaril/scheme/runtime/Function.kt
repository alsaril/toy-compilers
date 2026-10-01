package com.alsaril.scheme.runtime

@FunctionalInterface
interface Function {
    fun call(args: Any): Any
}