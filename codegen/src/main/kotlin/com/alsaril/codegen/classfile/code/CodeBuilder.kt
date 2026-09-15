package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.classfile.Fragment
import com.alsaril.codegen.classfile.OutstandingPatches
import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.classfile.attributes.StackMapFrame
import com.alsaril.codegen.classfile.recordAt
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import kotlin.math.max

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
    private val unpatched = OutstandingPatches()
    private var maxStack = 0
    private var maxLocals = 0

    fun build(): Fragment {
        frozen?.let { throw IllegalStateException("this builder has already been built") }
        with(bytecode.toByteArray()) {
            frozen = this
            return Fragment(listOf(this), frames, exceptionHandlers, maxStack, maxLocals, this.size)
                .also {
                    it.unpatchedJumps = unpatched::jumps
                    it.unpatchedHandlers = unpatched::handlers
                }
        }
    }

    fun loc() = bytecode.size

    internal fun deferredJump(patch: (Int) -> Unit): (Int) -> Unit {
        unpatched.jumps++
        var pending = true
        return { target ->
            if (pending) {
                pending = false
                unpatched.jumps--
            }
            patch(target)
        }
    }

    private fun deferredHandler(record: (Int) -> Unit): (Int) -> Unit {
        unpatched.handlers++
        var pending = true
        return { handlerPc ->
            check(pending) { "this catch has already been given a handler" }
            pending = false
            unpatched.handlers--
            record(handlerPc)
        }
    }

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
        frozen?.let { throw IllegalStateException("building freezes the code, so nothing more can be emitted") }
        bytecode.add(value.toByte())
    }

    private fun putAt(value: Int, pos: Int) {
        frozen?.let { it[pos] = value.toByte() } ?: run {
            bytecode[pos] = value.toByte()
        }
    }

    internal fun frame(build: (offsetDelta: Int) -> StackMapFrame) {
        val l = loc()
        if (frames.isNotEmpty() && l == base - 1) return
        frames.add(build(l - base))
        base = l + 1
    }

    fun fragment(fragment: Fragment) {
        require(fragment.unpatchedJumps() == 0) {
            "splicing copies the bytes of a fragment, so its ${fragment.unpatchedJumps()} " +
                    "outstanding jump patch(es) would not reach the copy: patch before splicing"
        }
        require(fragment.unpatchedHandlers() == 0) {
            "splicing copies the handler rows of a fragment, so its ${fragment.unpatchedHandlers()} " +
                    "outstanding handler patch(es) would not reach the copy: patch before splicing"
        }

        val pos = loc()
        fragment.content.forEach { chunk -> chunk.forEach(bytecode::add) }
        base = fragment.recordAt(pos, base, frames, exceptionHandlers)
        maxStack(fragment.maxStack)
        maxLocals(fragment.maxLocals)
    }

    fun `try`() = TryPointer(loc())

    fun `catch`(from: TryPointer, type: ClassPointer?): (Int) -> Unit {
        val to = loc()
        return deferredHandler { handlerPc ->
            exceptionHandlers.add(
                ExceptionHandler(from.index, to, handlerPc, type?.index ?: 0)
            )
        }
    }

    fun maxStack(depth: Int) {
        maxStack = max(maxStack, depth)
    }


    fun maxLocals(count: Int) {
        maxLocals = max(maxLocals, count)
    }

    internal fun local(slot: Int, slots: Int = 1) {
        maxLocals(slot + slots)
    }

    fun splice(start: Int, end: Int): Splice = SpliceImpl(bytecode.subList(start, end).toList())

    private class SpliceImpl(val code: List<Byte>) : Splice

    fun append(splice: Splice) {
        splice as SpliceImpl
        bytecode.addAll(splice.code)
    }
}

sealed interface Splice
