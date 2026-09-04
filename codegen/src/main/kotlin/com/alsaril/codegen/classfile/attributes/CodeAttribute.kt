package com.alsaril.codegen.classfile.attributes

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.write

class CodeAttribute(
    nameIndex: Int,
    private val maxStack: Int,
    private val maxLocals: Int,
    private val code: ByteArray,
    private val attributes: List<AttributeInfo>,
) : AttributeInfo(nameIndex) {
    override fun ClassWriter.writeContent() {
        short(maxStack)
        short(maxLocals)
        int(code.size)
        bytes(code)
        short(0) // no exception handling
        short(attributes.size)
        attributes.forEach(::write)
    }
}