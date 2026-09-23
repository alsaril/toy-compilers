package com.alsaril.codegen.code

import com.alsaril.codegen.classfile.*
import com.alsaril.codegen.classfile.AccessFlag.STATIC
import com.alsaril.codegen.code.BytecodeSerializer.serialize
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
    private val fields = mutableListOf<FieldInfo>()
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

    fun field(name: String, descriptor: String, vararg accessFlags: AccessFlag): ClassFileBuilder {
        val fieldInfo = FieldInfo(
            accessFlags.fold(0) { acc, flag -> acc or flag.value },
            cp.putUtf8(name),
            cp.putUtf8(descriptor),
            emptyList(),
        )
        fields.add(fieldInfo)
        return this
    }

    fun method(
        name: String,
        descriptor: String,
        vararg accessFlags: AccessFlag,
        codeBuilder: CodeBuilder.() -> Unit,
    ): ClassFileBuilder {
        val fragment = emitFragment { codeBuilder() }
        return method(name, descriptor, fragment, *accessFlags)
    }

    fun method(
        name: String,
        descriptor: String,
        fragment: Fragment,
        vararg accessFlags: AccessFlag,
    ): ClassFileBuilder {
        val d = parseFunctionDescriptor(descriptor)
        val headerSlots = d.argSlots(accessFlags.contains(STATIC))
        val code = serialize(fragment, headerSlots, cp::putUtf8)
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
        return newCodeBuilder().apply { codeBuilder() }.build()
    }

    fun build(): Pair<String, ByteArray> {
        val file = ClassFile(
            cp.putClass(thisName),
            cp.putClass(parentName),
            ifaces.map { cp.putClass(it) },
            fields,
            methods,
            cp.build(),
        )
        return thisName to toBytes { write(file) }
    }

    fun newCodeBuilder() = CodeBuilder(cp, thisName, parentName)

    companion object {
        fun classFile(name: String, parent: String) = ClassFileBuilder(name, parent)
    }
}