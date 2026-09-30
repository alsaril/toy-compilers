package com.alsaril.codegen.classfile.attributes

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable
import com.alsaril.codegen.write

class BootstrapMethodsAttribute(
    nameIndex: Int,
    private val entries: List<BootstrapMethod>
) : AttributeInfo(nameIndex) {
    override fun ClassWriter.writeContent() {
        u2(entries.size)
        entries.forEach(::write)
    }
}

data class BootstrapMethod(
    val bootstrapMethodRef: Int,
    val bootstrapArguments: List<Int>,
) : Writable {
    override fun ClassWriter.write() {
        u2(bootstrapMethodRef)
        u2(bootstrapArguments.size)
        bootstrapArguments.forEach(::u2)
    }
}
