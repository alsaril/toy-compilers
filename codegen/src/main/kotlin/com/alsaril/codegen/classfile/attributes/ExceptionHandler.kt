package com.alsaril.codegen.classfile.attributes

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable
import com.alsaril.codegen.constantpool.ClassPointer

data class ExceptionHandler(
    val startPc: Int,
    val endPc: Int,
    val handlerPc: Int,
    val catchType: ClassPointer?,
) : Writable {
    init {
        require(startPc < endPc) {
            "exception range [$startPc, $endPc) covers no instruction, so it must not be recorded"
        }
    }

    override fun ClassWriter.write() {
        u2(startPc)
        u2(endPc)
        u2(handlerPc)
        u2(catchType?.index ?: 0)
    }

    fun shift(delta: Int) = copy(startPc = startPc + delta, endPc = endPc + delta, handlerPc = handlerPc + delta)
}
