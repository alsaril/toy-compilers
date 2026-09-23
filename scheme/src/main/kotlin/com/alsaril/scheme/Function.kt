package com.alsaril.scheme

@FunctionalInterface
interface Function {
    fun call(args: Any): Any
}