package com.alsaril.codegen

fun load(bytes: ByteArray): Class<*> = object : ClassLoader() {
    fun define() = defineClass(null, bytes, 0, bytes.size)
}.define()
