package com.alsaril.codegen


object Compiler {
    @Suppress("UNCHECKED_CAST")
    fun <T, I> pipeline(
        expr: String,
        parse: (String) -> I,
        generate: (I) -> ClassGraph,
        programInterface: Class<T>,
    ): T {
        val instructions = parse(expr) // frontend
        val def = generate(instructions) // backend
        val loader = ByteClassLoader(def.deps)
        val root = def.root.let { (name, code) -> loader.loadClass(name, code) }
        require(programInterface.isAssignableFrom(root)) { // sanity check
            "generated class ${root.name} does not implement ${programInterface.name}"
        }
        return root.getDeclaredConstructor().newInstance() as T
    }
}
