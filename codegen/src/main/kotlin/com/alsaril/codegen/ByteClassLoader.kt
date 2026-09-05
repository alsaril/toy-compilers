package com.alsaril.codegen


object ByteClassLoader {

    private class SingleClassLoader : ClassLoader() {
        fun define(name: String, code: ByteArray): Class<*> = defineClass(name, code, 0, code.size)
    }

    fun loadClass(name: String, code: ByteArray): Class<*> = SingleClassLoader().define(name, code)
}
