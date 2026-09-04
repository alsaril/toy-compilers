package bf.compiler.attributes

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable
import com.alsaril.codegen.classfile.attributes.AttributeInfo
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

interface StackMapFrame : Writable

fun sameFrame(offsetDelta: Int) = if (offsetDelta <= 63) SameFrame(offsetDelta) else SameFrameExtended(offsetDelta)

data class SameFrame(
    val frameType: Int,
) : StackMapFrame {
    init {
        require(frameType in 0..63)
    }

    override fun ClassWriter.write() {
        byte(frameType)
    }
}

data class SameFrameExtended(
    val offsetDelta: Int,
) : StackMapFrame {
    override fun ClassWriter.write() {
        byte(251) // frame_type
        short(offsetDelta)
    }
}

data class AppendFrame(
    val offsetDelta: Int,
    val locals: List<VerificationTypeInfo>,
) : StackMapFrame {
    init {
        require(locals.size <= 3)
    }

    override fun ClassWriter.write() {
        byte(251 + locals.size) // frame_type
        short(offsetDelta)
        locals.forEach(::write)
    }
}

data class FullFrame(
    val offsetDelta: Int,
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