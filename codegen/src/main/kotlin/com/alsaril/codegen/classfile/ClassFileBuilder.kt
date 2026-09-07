package com.alsaril.codegen.classfile

import com.alsaril.codegen.classfile.attributes.CodeAttribute
import com.alsaril.codegen.classfile.attributes.StackMapTableAttribute
import com.alsaril.codegen.classfile.code.CodeBuilder
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import com.alsaril.codegen.toBytes
import com.alsaril.codegen.write
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class ClassFileBuilder {
    private val thisName: String
    private val parentName: String
    private val ifaces = mutableListOf<String>()
    private val methods = mutableListOf<MethodInfo>()

    private val cp = UpdatableConstantPool()

    private constructor(name: String, parent: String) {
        this.thisName = name
        this.parentName = parent
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
        val fragment = CodeBuilder(cp, thisName, parentName).apply { codeBuilder() }.build()
        return method(name, descriptor, fragment, maxStack, maxLocals, *accessFlags)
    }

    fun method(
        name: String,
        descriptor: String,
        fragment: Fragment,
        maxStack: Int,
        maxLocals: Int,
        vararg accessFlags: MethodAccessFlag,
    ): ClassFileBuilder {
        val code = CodeAttribute(
            cp.putUtf8("Code"),
            maxStack,
            maxLocals,
            fragment.bytecode(),
            listOf(StackMapTableAttribute(cp.putUtf8("StackMapTable"), fragment.frames)),
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

    @OptIn(ExperimentalContracts::class)
    fun emitFragment(codeBuilder: CodeBuilder.() -> Unit): Fragment {
        contract {
            callsInPlace(codeBuilder, InvocationKind.EXACTLY_ONCE)
        }
        return CodeBuilder(cp, thisName, parentName)
            .apply { codeBuilder() }
            .build()
    }

    fun build(): Pair<String, ByteArray> {
        val file = ClassFile(
            cp.putClass(thisName),
            cp.putClass(parentName),
            ifaces.map { cp.putClass(it) },
            methods,
            cp.build(),
        )
        return thisName to toBytes { write(file) }
    }

    companion object {
        fun classFile(name: String, parent: String) = ClassFileBuilder(name, parent)
    }
}