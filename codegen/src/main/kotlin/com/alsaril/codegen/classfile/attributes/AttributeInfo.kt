package com.alsaril.codegen.classfile.attributes

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable
import com.alsaril.codegen.toBytes

abstract class AttributeInfo(
    private val nameIndex: Int,
) : Writable {
    final override fun ClassWriter.write() {
        val content = toBytes { writeContent() }
        u2(nameIndex)
        int(content.size)
        bytes(content)
    }

    abstract fun ClassWriter.writeContent()
}