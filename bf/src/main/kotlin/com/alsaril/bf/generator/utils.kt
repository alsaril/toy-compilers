package com.alsaril.bf.generator

import com.alsaril.codegen.code.Fragment

class Generation(
    val bodyLengthLimit: Int,
    val loopOverhead: Int,
    private var counter: Int = 0,
) {
    fun nextIndex() = counter++
}

class Chunk(val fragments: List<Fragment>, val next: Int?)

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
            return Chunk(result, i)
        }
        result.add(fragment)
        size += fragment.size
        i++
    }
    return Chunk(result, null)
}