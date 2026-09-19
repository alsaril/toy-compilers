package com.alsaril.codegen.classfile.attributes

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable
import com.alsaril.codegen.write


class StackMapTableAttribute(
    nameIndex: Int,
    private val entries: List<StackMapFrame>
) : AttributeInfo(nameIndex) {
    override fun ClassWriter.writeContent() {
        u2(entries.size)
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
        u1(offsetDelta)
    }
}

data class SameFrameExtended(
    override val offsetDelta: Int,
) : StackMapFrame {
    override fun ClassWriter.write() {
        u1(251) // frame_type
        u2(offsetDelta)
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
        u1(offsetDelta + 64)
        write(stack)
    }
}

data class SameLocals1StackItemFrameExtended(
    override val offsetDelta: Int,
    override val stack: VerificationTypeInfo,
) : SameLocals1StackItemFrame {
    override fun ClassWriter.write() {
        u1(247) // frame_type
        u2(offsetDelta)
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
        u1(251 + locals.size) // frame_type
        u2(offsetDelta)
        locals.forEach(::write)
    }
}

data class FullFrame(
    override val offsetDelta: Int,
    val locals: List<VerificationTypeInfo>,
    val stack: List<VerificationTypeInfo>,
) : StackMapFrame {
    override fun ClassWriter.write() {
        u1(255) // frame_type
        u2(offsetDelta)
        u2(locals.size)
        locals.forEach(::write)
        u2(stack.size)
        stack.forEach(::write)
    }
}

interface VerificationTypeInfo : Writable

enum class SimpleVerificationTypeInfo(
    private val tag: Int,
) : VerificationTypeInfo {
    TopVariableInfo(0),
    IntegerVariableInfo(1),
    FloatVariableInfo(2);

    override fun ClassWriter.write() = u1(tag)
}

data class ObjectVariableInfo(
    private val cpoolIndex: Int,
) : VerificationTypeInfo {
    override fun ClassWriter.write() {
        u1(7)
        u2(cpoolIndex)
    }
}