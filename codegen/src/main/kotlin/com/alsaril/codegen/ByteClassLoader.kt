package com.alsaril.codegen


class ByteClassLoader(deps: List<ClassDef> = emptyList()) : ClassLoader() {
    private val deps = deps.associate { (name, code) -> binaryName(name) to code }

    fun loadClass(name: String, code: ByteArray): Class<*> = define(binaryName(name), code)

    override fun findClass(name: String): Class<*> {
        val code = deps[name] ?: throw ClassNotFoundException(name)
        return define(name, code)
    }

    private fun define(name: String, code: ByteArray): Class<*> {
        ClassDump.dump(name, code)
        return defineClass(name, code, 0, code.size)
    }

    private fun binaryName(name: String) = name.replace('/', '.')
}
