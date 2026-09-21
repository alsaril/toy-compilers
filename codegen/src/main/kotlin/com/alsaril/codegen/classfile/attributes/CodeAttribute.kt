package com.alsaril.codegen.classfile.attributes

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.write

class CodeAttribute(
    nameIndex: Int,
    private val maxStack: Int,
    private val maxLocals: Int,
    private val code: ByteArray,
    private val exceptionHandlers: List<ExceptionHandler>,
    private val attributes: List<AttributeInfo>,
) : AttributeInfo(nameIndex) {
    init {
        require(code.isNotEmpty()) { "method has no code, code_length must be at least 1" }
        require(code.size <= 0xffff) { "method is ${code.size} bytes, over the 65535 limit" }
    }

    override fun ClassWriter.writeContent() {
        u2(maxStack)
        u2(maxLocals)
        int(code.size)
        bytes(code)
        u2(exceptionHandlers.size)
        exceptionHandlers.forEach(::write)
        u2(attributes.size)
        attributes.forEach(::write)
    }
}