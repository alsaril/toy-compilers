package com.alsaril.codegen

import com.alsaril.codegen.debug.ClassDump
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodHandles.Lookup.ClassOption.NESTMATE
import java.lang.invoke.MethodType


object Compiler {
    @Suppress("UNCHECKED_CAST")
    fun <T, I> pipeline(
        expr: String,
        parse: (String) -> I,
        generate: (I) -> ByteArray,
        programInterface: Class<T>,
        lookup: MethodHandles.Lookup,
    ): T {
        val instructions = parse(expr) // frontend
        val bytes = generate(instructions) // backend
        ClassDump.dump(bytes)
        val hidden = lookup.defineHiddenClass(bytes, true, NESTMATE)
        val cls = hidden.lookupClass()
        require(programInterface.isAssignableFrom(cls)) { // sanity check
            "generated class ${cls.name} does not implement ${programInterface.name}"
        }
        val ctor = hidden.findConstructor(cls, MethodType.methodType(Void.TYPE))
        return ctor.invoke() as T
    }
}
