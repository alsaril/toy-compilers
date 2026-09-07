package com.alsaril.codegen.classfile.attributes

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable

data class ExceptionHandler(
    val startPc: Int,
    val endPc: Int,
    val handlerPc: Int,
    val catchType: Int
) : Writable {
    override fun ClassWriter.write() {
        short(startPc)
        short(endPc)
        short(handlerPc)
        short(catchType)
    }
}
