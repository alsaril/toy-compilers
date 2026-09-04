package com.alsaril.codegen.classfile

import com.alsaril.codegen.constantpool.UpdatableConstantPool
import com.alsaril.codegen.toBytes
import com.alsaril.codegen.write

class ClassFileBuilder {
    private val name: String
    private val parent: String
    private val ifaces = mutableListOf<String>()
    private val methods = mutableListOf<Method>()

    private val cp = UpdatableConstantPool()

    private constructor(name: String, parent: String) {
        this.name = name
        this.parent = parent
    }

    fun iface(name: String): ClassFileBuilder {
        ifaces.add(name)
        return this
    }

    fun method(
        name: String,
        signature: String,
        maxStack: Int,
        maxLocals: Int,
        vararg modifiers: MethodModifier,
        codeBuilder: CodeBuilder.() -> Unit,
    ): ClassFileBuilder {
        return this
    }

    fun build(): Pair<String, ByteArray> {
        val file = ClassFile(
            cp.build(),
            cp.putClass(name),
            cp.putClass(parent),
            ifaces.map { cp.putClass(it) },
            methods.map { it.info() },
        )
        return name to toBytes { write(file) }
    }

    companion object {
        fun classFile(name: String, parent: String) = ClassFileBuilder(name, parent)
    }
}

enum class MethodModifier {
    PUBLIC, PROTECTED, PRIVATE, STATIC, FINAL
}

interface CodeBuilder

interface Method {
    fun info(): MethodInfo
}