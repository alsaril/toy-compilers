package com.alsaril.codegen.classfile

import com.alsaril.codegen.classfile.attributes.StackMapTableAttribute
import com.alsaril.codegen.classfile.attributes.CodeAttribute
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import com.alsaril.codegen.toBytes
import com.alsaril.codegen.write

class ClassFileBuilder {
    private val name: String
    private val parent: String
    private val ifaces = mutableListOf<String>()
    private val methods = mutableListOf<MethodInfo>()

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
        descriptor: String,
        maxStack: Int,
        maxLocals: Int,
        vararg accessFlags: MethodAccessFlag,
        codeBuilder: CodeBuilder.() -> Unit,
    ): ClassFileBuilder {
        val (bytecode, stackMapFrames) = CodeBuilder(cp, this.name, parent).apply { codeBuilder() }.build()
        val code = CodeAttribute(
            cp.putUtf8("Code"),
            maxStack,
            maxLocals,
            bytecode,
            listOf(StackMapTableAttribute(cp.putUtf8("StackMapTable"), stackMapFrames)),
        )
        val methodInfo = MethodInfo(
            accessFlags.fold(0) { acc, flag -> acc or flag.value },
            cp.putUtf8(name),
            cp.putUtf8(descriptor),
            listOf(code),
        )
        methods.add(methodInfo)
        return this
    }

    fun build(): Pair<String, ByteArray> {
        val file = ClassFile(
            cp.putClass(name),
            cp.putClass(parent),
            ifaces.map { cp.putClass(it) },
            methods.toList(),
            cp.build(),
        )
        return name to toBytes { write(file) }
    }

    companion object {
        fun classFile(name: String, parent: String) = ClassFileBuilder(name, parent)
    }
}