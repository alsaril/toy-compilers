package com.alsaril.codegen.classfile.attributes

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable

data class ExceptionHandler(
    val startPc: Int,
    val endPc: Int,
    val handlerPc: Int,
    val catchType: Int
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
        u2(catchType)
    }

    fun shift(delta: Int) = copy(startPc = startPc + delta, endPc = endPc + delta, handlerPc = handlerPc + delta)
}
