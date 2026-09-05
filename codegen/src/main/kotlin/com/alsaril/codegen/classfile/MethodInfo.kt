package com.alsaril.codegen.classfile

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable
import com.alsaril.codegen.classfile.attributes.AttributeInfo
import com.alsaril.codegen.write

enum class MethodAccessFlag(val value: Int) {
    PUBLIC(0x0001), PRIVATE(0x0002), STATIC(0x0008), FINAL(0x0010)
}

data class MethodInfo(
    val accessFlags: Int,
    val nameIndex: Int,
    val descriptorIndex: Int,
    val attributes: List<AttributeInfo>,
) : Writable {
    override fun ClassWriter.write() {
        short(accessFlags)
        short(nameIndex)
        short(descriptorIndex)
        short(attributes.size)
        attributes.forEach(::write)
    }
}
