package com.alsaril.codegen.classfile

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable
import com.alsaril.codegen.classfile.attributes.AttributeInfo
import com.alsaril.codegen.write

data class FieldInfo(
    val accessFlags: Int,
    val nameIndex: Int,
    val descriptorIndex: Int,
    val attributes: List<AttributeInfo>,
) : Writable {
    override fun ClassWriter.write() {
        u2(accessFlags)
        u2(nameIndex)
        u2(descriptorIndex)
        u2(attributes.size)
        attributes.forEach(::write)
    }
}
