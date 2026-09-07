package com.alsaril.codegen.classfile

import com.alsaril.codegen.classfile.attributes.StackMapFrame
import java.nio.ByteBuffer

data class Fragment(
    val content: List<ByteArray>,
    val frames: List<StackMapFrame>,
    val size: Int, // the sum of lengths in content
) {
    fun bytecode(): ByteArray = if (content.size == 1) content.first() else
        ByteBuffer.allocate(size).apply { content.forEach(::put) }.array()
}