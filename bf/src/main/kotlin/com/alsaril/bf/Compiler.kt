package com.alsaril.bf

import com.alsaril.bf.Parser.parse
import com.alsaril.bf.generator.ClassGenerator.generate
import com.alsaril.codegen.ByteClassLoader.loadClass


object Compiler {
    fun compile(expr: String): Program {
        val instructions = parse(expr) // frontend
        val (name, code) = generate(instructions) // backend
        val clazz = loadClass(name, code)
        require(Program::class.java.isAssignableFrom(clazz)) { // sanity check
            "generated class $name does not implement ${Program::class.java.name}"
        }
        return clazz.getDeclaredConstructor().newInstance() as Program
    }
}
