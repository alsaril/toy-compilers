package com.alsaril.codegen.classfile

import com.alsaril.codegen.classfile.attributes.*
import java.nio.ByteBuffer
import kotlin.math.max

internal class UnpatchedJumps {
    var count = 0
}

data class Fragment(
    val content: List<ByteArray>,
    val frames: List<StackMapFrame>,
    val exceptionHandlers: List<ExceptionHandler>,
    val maxStack: Int,
    val size: Int, // the sum of lengths in content
) {
    internal var unpatchedJumps: () -> Int = { 0 }

    fun bytecode(): ByteArray = if (content.size == 1) content.first() else {
        require(unpatchedJumps() == 0) {
            "flattening several blocks copies them, so the ${unpatchedJumps()} outstanding " +
                "jump patch(es) would not reach the result: patch before flattening"
        }
        ByteBuffer.allocate(size).apply { content.forEach(::put) }.array()
    }
}

internal fun Fragment.recordAt(
    position: Int,
    base: Int,
    frames: MutableList<StackMapFrame>,
    handlers: MutableList<ExceptionHandler>,
): Int {
    var next = base

    this.frames.firstOrNull()?.let { first ->
        val patchedOffset = first.offsetDelta + position - next

        if (patchedOffset != -1) {
            val patched = first.movedTo(patchedOffset)
            frames.add(patched)
            next += patched.offsetDelta + 1
        }
    }

    this.frames.asSequence().drop(1).forEach {
        frames.add(it)
        next += it.offsetDelta + 1
    }

    exceptionHandlers.forEach { handlers.add(it.shiftedBy(position)) }

    return next
}

private fun StackMapFrame.movedTo(offsetDelta: Int) = when (this) {
    is SameFrame, is SameFrameExtended -> sameFrame(offsetDelta)
    is AppendFrame -> copy(offsetDelta = offsetDelta)
    is FullFrame -> copy(offsetDelta = offsetDelta)
    is SameLocals1StackItemFrame -> sameLocals1StackItem(offsetDelta, stack)
}

private fun ExceptionHandler.shiftedBy(offset: Int) = copy(
    startPc = startPc + offset,
    endPc = endPc + offset,
    handlerPc = handlerPc + offset,
)

fun List<Fragment>.join(): Fragment {
    if (size == 1) return first()

    val content = mutableListOf<ByteArray>()
    val frames = mutableListOf<StackMapFrame>()
    val handlers = mutableListOf<ExceptionHandler>()
    var base = 0
    var totalSize = 0
    var maxStack = 0

    forEach { fragment ->
        content.addAll(fragment.content)
        base = fragment.recordAt(totalSize, base, frames, handlers)
        maxStack = max(maxStack, fragment.maxStack)
        totalSize += fragment.size
    }

    return Fragment(content, frames, handlers, maxStack, totalSize)
        .also { joined -> joined.unpatchedJumps = { sumOf { it.unpatchedJumps() } } }
}
