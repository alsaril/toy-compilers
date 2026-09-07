package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.classfile.Fragment
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
    private var frozen: ByteArray? = null

    fun build(): Fragment {
        frozen?.let {  throw IllegalStateException() }
        with (bytecode.toByteArray()) {
            frozen = this
            return Fragment(listOf(this), frames, this.size)
        }
    }

    fun loc() = bytecode.size

    internal fun b1(value: Byte) {
        frozen?.let {  throw IllegalStateException() }
        bytecode.add(value)
    }

    internal fun b1(value: Int) {
        require(value in Byte.MIN_VALUE..0xff) { "$value does not fit a bytecode operand byte" }
        b1(value.toByte())
    }

    internal fun b2(value: Int) {
        require(value in Short.MIN_VALUE..0xffff) { "$value does not fit a bytecode operand short" }
        b1(value shr 8)
        b1(value and 0xff)
    }

    internal fun b1At(value: Int, pos: Int) {
        frozen?.let { it[pos] = value.toByte() } ?: run {
            bytecode[pos] = value.toByte()
        }
    }

    internal fun b2At(value: Int, pos: Int) {
        b1At(value shr 8, pos)
        b1At(value and 0xff, pos + 1)
    }

    // a frame is positioned relative to the previous one, so base stays here
    internal fun frame(build: (offsetDelta: Int) -> StackMapFrame) {
        val l = loc()
        // frame offsets must strictly increase, so an offset already covered by the
        // previous frame keeps it rather than recording a second one there
        if (frames.isNotEmpty() && l == base - 1) return
        frames.add(build(l - base))
        base = l + 1
    }
}
