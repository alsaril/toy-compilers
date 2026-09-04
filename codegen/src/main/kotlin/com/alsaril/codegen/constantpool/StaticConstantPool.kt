package com.alsaril.codegen.constantpool

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable
import com.alsaril.codegen.write

data class StaticConstantPool(
    val size: Int,
    val entries: List<CpInfo>,
) : Writable {
    override fun ClassWriter.write() {
        short(size)
        entries.forEach(::write)
    }
}