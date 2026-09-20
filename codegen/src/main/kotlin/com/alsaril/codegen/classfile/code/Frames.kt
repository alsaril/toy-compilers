package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.classfile.attributes.*
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.FloatVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.IntegerVariableInfo

sealed interface VarInfo

data object IntInfo : VarInfo

data object FloatInfo : VarInfo

fun CodeBuilder.objInfo(pointer: ClassPointer): VarInfo = ObjInfo(pointer.index)
fun CodeBuilder.objInfo(name: String): VarInfo = ObjInfo(clazz(name).index)

internal data class ObjInfo(val index: Int) : VarInfo

fun CodeBuilder.frameSame(label: Label) = frame(sameFrame(label.index))

fun CodeBuilder.frameStack(label: Label, varInfo: VarInfo) = frame(
    sameLocals1StackItem(label.index, varInfo2Writable(varInfo))
)

fun CodeBuilder.frameAppend(label: Label, vararg varInfos: VarInfo) = frame(
    AppendFrame(label.index, varInfos.map(::varInfo2Writable))
)

fun CodeBuilder.frameFull(label: Label, locals: List<VarInfo>, stack: List<VarInfo>) = frame(
    FullFrame(label.index, locals.map(::varInfo2Writable), stack.map(::varInfo2Writable))
)

private fun varInfo2Writable(varInfo: VarInfo) = when (varInfo) {
    IntInfo -> IntegerVariableInfo
    FloatInfo -> FloatVariableInfo
    is ObjInfo -> ObjectVariableInfo(varInfo.index)
}

fun patchOffset(frame: StackMapFrame, newOffset: Int) = when (frame) {
    is AppendFrame -> frame.copy(offsetDelta = newOffset)
    is FullFrame -> frame.copy(offsetDelta = newOffset)
    is SameFrame, is SameFrameExtended -> sameFrame(offsetDelta = newOffset)
    is SameLocals1StackItemFrame -> sameLocals1StackItem(offsetDelta = newOffset, frame.stack)
}