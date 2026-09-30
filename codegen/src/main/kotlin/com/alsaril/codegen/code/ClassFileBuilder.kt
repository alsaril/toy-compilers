package com.alsaril.codegen.code

import com.alsaril.codegen.ClassDef
import com.alsaril.codegen.classfile.AccessFlag
import com.alsaril.codegen.classfile.AccessFlag.STATIC
import com.alsaril.codegen.classfile.ClassFile
import com.alsaril.codegen.classfile.FieldInfo
import com.alsaril.codegen.classfile.MethodInfo
import com.alsaril.codegen.classfile.attributes.AttributeInfo
import com.alsaril.codegen.classfile.attributes.BootstrapMethodsAttribute
import com.alsaril.codegen.code.BytecodeSerializer.serialize
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import com.alsaril.codegen.toBytes
import com.alsaril.codegen.write
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class ClassFileBuilder {
    internal val thisName: String
    private val parentName: String
    private val ifaces = mutableListOf<String>()
    private val fields = mutableListOf<FieldInfo>()
    private val methods = mutableListOf<MethodInfo>()
    private val attributes = mutableListOf<AttributeInfo>()
    private val bootstrapMethods = BootstrapMethods()

    internal val cp = UpdatableConstantPool()
    internal val hierarchy: ClassHierarchy

    private constructor(name: String, parent: String, hierarchy: ClassHierarchy) {
        this.thisName = name
        this.parentName = parent
        this.hierarchy = hierarchy
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
        val code = serialize(fragment, descriptor, constructor = name == "<init>", static = accessFlags.contains(STATIC))
        val methodInfo = MethodInfo(
            accessFlags.fold(0) { acc, flag -> acc or flag.value },
            cp.putUtf8(name),
            cp.putUtf8(descriptor),
            listOf(code),
        )
        methods.add(methodInfo)
        return this
    }

    fun attribute(attributeInfo: AttributeInfo): ClassFileBuilder {
        attributes.add(attributeInfo)
        return this
    }

    @OptIn(ExperimentalContracts::class)
    fun emitFragment(codeBuilder: CodeBuilder.() -> Unit): Fragment {
        contract {
            callsInPlace(codeBuilder, InvocationKind.EXACTLY_ONCE)
        }
        return newCodeBuilder().apply { codeBuilder() }.build()
    }

    fun build(): ClassDef {
        val attributes = if (bootstrapMethods.isEmpty()) attributes.toList() else
            attributes + BootstrapMethodsAttribute(cp.putUtf8("BootstrapMethods"), bootstrapMethods.methods())
        val file = ClassFile(
            cp.putClass(thisName),
            cp.putClass(parentName),
            ifaces.map { cp.putClass(it) },
            fields.toList(),
            methods.toList(),
            attributes,
            cp.build(),
        )
        return thisName to toBytes { write(file) }
    }

    fun newCodeBuilder() = CodeBuilder(cp, bootstrapMethods, thisName, parentName)

    companion object {
        fun classFile(name: String, parent: String, hierarchy: ClassHierarchy = LenientHierarchy) =
            ClassFileBuilder(name, parent, hierarchy)
    }
}