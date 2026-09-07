package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.classfile.Fragment
import com.alsaril.codegen.classfile.attributes.ExceptionHandler
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
    private val exceptionHandlers = mutableListOf<ExceptionHandler>()
    private var base = 0
    private var frozen: ByteArray? = null

    fun build(): Fragment {
        frozen?.let { throw IllegalStateException() }
        with(bytecode.toByteArray()) {
            frozen = this
            return Fragment(listOf(this), frames, exceptionHandlers, this.size)
        }
    }

    fun loc() = bytecode.size

    internal fun u1(value: Int) {
        require(value in 0..0xff) { "$value does not fit a u1" }
        put(value)
    }

    internal fun s1(value: Int) {
        require(value in Byte.MIN_VALUE..Byte.MAX_VALUE) { "$value does not fit an s1" }
        put(value)
    }

    internal fun u2(value: Int) {
        require(value in 0..0xffff) { "$value does not fit a u2" }
        put(value shr 8)
        put(value)
    }

    internal fun s2(value: Int) {
        require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "$value does not fit an s2" }
        put(value shr 8)
        put(value)
    }

    internal fun s2At(value: Int, pos: Int) {
        require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "$value does not fit an s2" }
        putAt(value shr 8, pos)
        putAt(value, pos + 1)
    }

    private fun put(value: Int) {
        frozen?.let { throw IllegalStateException() }
        bytecode.add(value.toByte())
    }

    private fun putAt(value: Int, pos: Int) {
        frozen?.let { it[pos] = value.toByte() } ?: run {
            bytecode[pos] = value.toByte()
        }
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

    fun `try`() = TryPointer(loc())

    fun `catch`(from: TryPointer, type: ClassPointer?): (Int) -> Unit {
        val to = loc()
        return {
            exceptionHandlers.add(
                ExceptionHandler(from.index, to, it, type?.index ?: 0)
            )
        }
    }
}
