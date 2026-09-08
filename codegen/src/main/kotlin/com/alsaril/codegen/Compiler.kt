package com.alsaril.codegen

import com.alsaril.codegen.ByteClassLoader.loadClass


object Compiler {
    @Suppress("UNCHECKED_CAST")
    fun <T, I> pipeline(
        expr: String,
        parse: (String) -> I,
        generate: (I) -> Pair<String, ByteArray>,
        programInterface: Class<T>
    ): T {
        val instructions = parse(expr) // frontend
        val (name, code) = generate(instructions) // backend
        val clazz = loadClass(name, code)
        require(programInterface.isAssignableFrom(clazz)) { // sanity check
            "generated class $name does not implement ${programInterface.name}"
        }
        return clazz.getDeclaredConstructor().newInstance() as T
    }
}
