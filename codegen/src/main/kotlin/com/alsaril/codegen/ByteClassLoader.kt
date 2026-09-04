package com.alsaril.codegen


object ByteClassLoader : ClassLoader() {
    fun loadClass(name: String, code: ByteArray): Class<*> = defineClass(name, code, 0, code.size)
}