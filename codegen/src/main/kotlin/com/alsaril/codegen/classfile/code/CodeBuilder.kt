package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.classfile.attributes.StackMapFrame
import com.alsaril.codegen.constantpool.UpdatableConstantPool

@DslMarker
annotation class CodeDsl

@CodeDsl
class CodeBuilder(
    internal val cp: UpdatableConstantPool,
    internal val thisClass: String,
    internal val parentClass: String,
) {
    private val bytecode = mutableListOf<Byte>()
    private val frames = mutableListOf<StackMapFrame>()
    private var base = 0

    fun build(): Pair<ByteArray, List<StackMapFrame>> = bytecode.toByteArray() to frames

    fun loc() = bytecode.size

    internal fun b1(value: Int) {
        bytecode.add(value.toByte())
    }

    internal fun b2(value: Int) {
        b1(value shr 8)
        b1(value and 0xff)
    }

    internal fun b1At(value: Int, pos: Int) {
        bytecode[pos] = value.toByte()
    }

    internal fun b2At(value: Int, pos: Int) {
        b1At(value shr 8, pos)
        b1At(value and 0xff, pos + 1)
    }

    // a frame is positioned relative to the previous one, so base stays here
    internal fun frame(build: (offsetDelta: Int) -> StackMapFrame) {
        val l = loc()
        frames.add(build(l - base))
        base = l + 1
    }
}
