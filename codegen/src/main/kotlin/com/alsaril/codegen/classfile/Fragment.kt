package com.alsaril.codegen.classfile

import com.alsaril.codegen.classfile.attributes.AppendFrame
import com.alsaril.codegen.classfile.attributes.FullFrame
import com.alsaril.codegen.classfile.attributes.SameFrame
import com.alsaril.codegen.classfile.attributes.SameFrameExtended
import com.alsaril.codegen.classfile.attributes.StackMapFrame
import com.alsaril.codegen.classfile.attributes.sameFrame
import java.nio.ByteBuffer

data class Fragment(
    val content: List<ByteArray>,
    val frames: List<StackMapFrame>,
    val size: Int, // the sum of lengths in content
) {
    fun bytecode(): ByteArray = if (content.size == 1) content.first() else
        ByteBuffer.allocate(size).apply { content.forEach(::put) }.array()
}

fun List<Fragment>.join(): Fragment {
    if (size == 1) return first()
    val result = mutableListOf<ByteArray>()
    val globalFrames = mutableListOf<StackMapFrame>()
    var globalBase = 0
    var globalSize = 0

    forEach { (code, frames, size) ->
        result.addAll(code)
        if (frames.isNotEmpty()) {
            val frame = frames.first()
            val patchedOffset = frame.offsetDelta + globalSize - globalBase
            if (patchedOffset != -1) {
                val patched = when (frame) {
                    is SameFrame, is SameFrameExtended -> sameFrame(patchedOffset)
                    is AppendFrame -> frame.copy(offsetDelta = patchedOffset)
                    is FullFrame -> frame.copy(offsetDelta = patchedOffset)
                }
                globalFrames.add(patched)
                globalBase += patched.offsetDelta + 1
            }
        }
        frames.asSequence().drop(1).forEach {
            globalFrames.add(it)
            globalBase += it.offsetDelta + 1
        }
        globalSize += size
    }

    return Fragment(result, globalFrames, globalSize)
}