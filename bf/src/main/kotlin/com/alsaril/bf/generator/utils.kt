package com.alsaril.bf.generator

import com.alsaril.codegen.classfile.Fragment

data class MutableInt(var value: Int = 0) {
    fun inc() = value++
}

class Generation(
    val bodyLengthLimit: Int,
    val loopOverhead: Int,
    val counter: MutableInt = MutableInt(),
)

data class LoopBoundary(
    val fragment: Fragment,
    val exit: (Int) -> Unit,
    val entry: Int
)

data class Chunk(val fragments: List<Fragment>, val size: Int, val next: Int?)

fun collect(
    fragments: List<Fragment>,
    start: Int,
    maxsize: Int,
    allowSingleFragmentSpill: Boolean = false
): Chunk {
    val result = mutableListOf<Fragment>()
    var size = 0
    var i = start
    while (i < fragments.size) {
        val fragment = fragments[i]
        if (size + fragment.size > maxsize && !(result.isEmpty() && allowSingleFragmentSpill)) {
            return Chunk(result, size, i)
        }
        result.add(fragment)
        size += fragment.size
        i++
    }
    return Chunk(result, size, null)
}