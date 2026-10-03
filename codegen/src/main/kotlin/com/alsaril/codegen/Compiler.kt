package com.alsaril.codegen

import com.alsaril.codegen.debug.ClassDump
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

class ClassOutput(val bytes: ByteArray, val classData: Any? = null)

object Compiler {
    @Suppress("UNCHECKED_CAST")
    fun <T, I> pipeline(
        expr: String,
        parse: (String) -> I,
        generate: (I) -> ClassOutput,
        programInterface: Class<T>,
        lookup: MethodHandles.Lookup,
    ): T {
        val instructions = parse(expr) // frontend
        val generated = generate(instructions) // backend
        ClassDump.dump(generated.bytes)
        val hidden = when (val classData = generated.classData) {
            null -> lookup.defineHiddenClass(generated.bytes, true)
            else -> lookup.defineHiddenClassWithClassData(generated.bytes, classData, true)
        }
        val cls = hidden.lookupClass()
        require(programInterface.isAssignableFrom(cls)) { // sanity check
            "generated class ${cls.name} does not implement ${programInterface.name}"
        }
        val ctor = hidden.findConstructor(cls, MethodType.methodType(Void.TYPE))
        return ctor.invoke() as T
    }
}
