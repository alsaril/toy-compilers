package com.alsaril.codegen.classfile.attributes

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable
import com.alsaril.codegen.write


class StackMapTableAttribute(
    nameIndex: Int,
    private val entries: List<StackMapFrame>
) : AttributeInfo(nameIndex) {
    override fun ClassWriter.writeContent() {
        short(entries.size)
        entries.forEach(::write)
    }
}

sealed interface StackMapFrame : Writable {
    val offsetDelta: Int
}

fun sameFrame(offsetDelta: Int) = if (offsetDelta <= 63) SameFrame(offsetDelta) else SameFrameExtended(offsetDelta)

data class SameFrame(
    override val offsetDelta: Int,
) : StackMapFrame {
    init {
        require(offsetDelta in 0..63)
    }

    override fun ClassWriter.write() {
        byte(offsetDelta)
    }
}

data class SameFrameExtended(
    override val offsetDelta: Int,
) : StackMapFrame {
    override fun ClassWriter.write() {
        byte(251) // frame_type
        short(offsetDelta)
    }
}

fun sameLocals1StackItem(offsetDelta: Int, stack: VerificationTypeInfo) =
    if (offsetDelta <= 63) SameLocals1StackItemFrameShort(offsetDelta, stack)
    else SameLocals1StackItemFrameExtended(offsetDelta, stack)

interface SameLocals1StackItemFrame : StackMapFrame {
    override val offsetDelta: Int
    val stack: VerificationTypeInfo
}

data class SameLocals1StackItemFrameShort(
    override val offsetDelta: Int,
    override val stack: VerificationTypeInfo,
) : SameLocals1StackItemFrame {
    init {
        require(offsetDelta in 0..63)
    }

    override fun ClassWriter.write() {
        byte(offsetDelta + 64)
        write(stack)
    }
}

data class SameLocals1StackItemFrameExtended(
    override val offsetDelta: Int,
    override val stack: VerificationTypeInfo,
) : SameLocals1StackItemFrame {
    override fun ClassWriter.write() {
        byte(247) // frame_type
        short(offsetDelta)
        write(stack)
    }
}

data class AppendFrame(
    override val offsetDelta: Int,
    val locals: List<VerificationTypeInfo>,
) : StackMapFrame {
    init {
        require(locals.size in 1..3)
    }

    override fun ClassWriter.write() {
        byte(251 + locals.size) // frame_type
        short(offsetDelta)
        locals.forEach(::write)
    }
}

data class FullFrame(
    override val offsetDelta: Int,
    val locals: List<VerificationTypeInfo>,
    val stack: List<VerificationTypeInfo>,
) : StackMapFrame {
    override fun ClassWriter.write() {
        byte(255) // frame_type
        short(offsetDelta)
        short(locals.size)
        locals.forEach(::write)
        short(stack.size)
        stack.forEach(::write)
    }
}

interface VerificationTypeInfo : Writable

enum class SimpleVerificationTypeInfo(
    private val tag: Int,
) : VerificationTypeInfo {
    TopVariableInfo(0),
    IntegerVariableInfo(1);

    override fun ClassWriter.write() = byte(tag)
}

data class ObjectVariableInfo(
    private val cpoolIndex: Int,
) : VerificationTypeInfo {
    override fun ClassWriter.write() {
        byte(7)
        short(cpoolIndex)
    }
}