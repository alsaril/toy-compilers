package com.alsaril.codegen.classfile

import com.alsaril.codegen.classfile.attributes.*
import java.nio.ByteBuffer

data class Fragment(
    val content: List<ByteArray>,
    val frames: List<StackMapFrame>,
    val exceptionHandlers: List<ExceptionHandler>,
    val size: Int, // the sum of lengths in content
) {
    fun bytecode(): ByteArray = if (content.size == 1) content.first() else
        ByteBuffer.allocate(size).apply { content.forEach(::put) }.array()
}

fun List<Fragment>.join(): Fragment {
    if (size == 1) return first()
    val result = mutableListOf<ByteArray>()
    val globalFrames = mutableListOf<StackMapFrame>()
    val globalExceptionHandlers = mutableListOf<ExceptionHandler>()
    var globalBase = 0
    var globalSize = 0

    forEach { (code, frames, exceptionHandlers, size) ->
        result.addAll(code)
        if (frames.isNotEmpty()) {
            val frame = frames.first()
            val patchedOffset = frame.offsetDelta + globalSize - globalBase
            if (patchedOffset != -1) {
                val patched = when (frame) {
                    is SameFrame, is SameFrameExtended -> sameFrame(patchedOffset)
                    is AppendFrame -> frame.copy(offsetDelta = patchedOffset)
                    is FullFrame -> frame.copy(offsetDelta = patchedOffset)
                    is SameLocals1StackItemFrame -> sameLocals1StackItem(patchedOffset, frame.stack)
                }
                globalFrames.add(patched)
                globalBase += patched.offsetDelta + 1
            }
        }
        frames.asSequence().drop(1).forEach {
            globalFrames.add(it)
            globalBase += it.offsetDelta + 1
        }
        exceptionHandlers.asSequence().map {
            it.copy(
                startPc = it.startPc + globalSize,
                endPc = it.endPc + globalSize,
                handlerPc = it.handlerPc + globalSize,
            )
        }.forEach(globalExceptionHandlers::add)
        globalSize += size
    }

    return Fragment(result, globalFrames, globalExceptionHandlers, globalSize)
}