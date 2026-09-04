package com.alsaril.codegen.classfile

import bf.compiler.attributes.StackMapFrame
import bf.compiler.attributes.StackMapTableAttribute
import com.alsaril.codegen.classfile.attributes.CodeAttribute
import com.alsaril.codegen.constantpool.UpdatableConstantPool

enum class MethodAccessFlag(val value: Int) {
    PUBLIC(0x0001), PRIVATE(0x0002), STATIC(0x0008), FINAL(0x0010)
}

data class Method(
    val name: String,
    val descriptor: String,
    val maxStack: Int,
    val maxLocals: Int,
    val accessFlags: List<MethodAccessFlag>,
    val bytecode: ByteArray,
    val stackMapFrames: List<StackMapFrame>,
) {
    fun info(cp: UpdatableConstantPool) = MethodInfo(
        accessFlags.map { it.value }.reduce { acc, i -> acc or i },
        cp.putUtf8(name),
        cp.putUtf8(descriptor),
        listOf(
            CodeAttribute(
                cp.putUtf8("Code"),
                maxStack,
                maxLocals,
                bytecode,
                listOf(StackMapTableAttribute(cp.putUtf8("StackMapTable"), stackMapFrames))
            )
        )
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Method

        if (maxStack != other.maxStack) return false
        if (maxLocals != other.maxLocals) return false
        if (name != other.name) return false
        if (descriptor != other.descriptor) return false
        if (accessFlags != other.accessFlags) return false
        if (!bytecode.contentEquals(other.bytecode)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = maxStack
        result = 31 * result + maxLocals
        result = 31 * result + name.hashCode()
        result = 31 * result + descriptor.hashCode()
        result = 31 * result + accessFlags.hashCode()
        result = 31 * result + bytecode.contentHashCode()
        return result
    }
}