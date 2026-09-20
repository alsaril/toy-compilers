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

fun CodeBuilder.frameSame(label: Label) = frame(label, SameFrame(0))

fun CodeBuilder.frameStack(label: Label, varInfo: VarInfo) = frame(
    label,
    SameLocals1StackItemFrameShort(0, varInfo2Writable(varInfo))
)

fun CodeBuilder.frameAppend(label: Label, vararg varInfos: VarInfo) = frame(
    label,
    AppendFrame(0, varInfos.map(::varInfo2Writable))
)

fun CodeBuilder.frameFull(label: Label, locals: List<VarInfo>, stack: List<VarInfo>) = frame(
    label,
    FullFrame(0, locals.map(::varInfo2Writable), stack.map(::varInfo2Writable))
)

private fun varInfo2Writable(varInfo: VarInfo) = when (varInfo) {
    IntInfo -> IntegerVariableInfo
    FloatInfo -> FloatVariableInfo
    is ObjInfo -> ObjectVariableInfo(varInfo.index)
}