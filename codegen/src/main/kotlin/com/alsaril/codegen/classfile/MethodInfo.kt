package com.alsaril.codegen.classfile

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable
import com.alsaril.codegen.classfile.attributes.AttributeInfo
import com.alsaril.codegen.write

class MethodInfo(
    private val accessFlags: Int,
    private val nameIndex: Int,
    private val descriptorIndex: Int,
    private val attributes: List<AttributeInfo>,
) : Writable {
    override fun ClassWriter.write() {
        short(accessFlags)
        short(nameIndex)
        short(descriptorIndex)
        short(attributes.size)
        attributes.forEach(::write)
    }
}