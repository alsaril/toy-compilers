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

fun CodeBuilder.frameSame(instruction: Instruction) = frame(instruction, SameFrame(0))

fun CodeBuilder.frameStack(instruction: Instruction, varInfo: VarInfo) = frame(
    instruction,
    SameLocals1StackItemFrameShort(0, varInfo2Writable(varInfo))
)

fun CodeBuilder.frameAppend(instruction: Instruction, vararg varInfos: VarInfo) = frame(
    instruction,
    AppendFrame(0, varInfos.map(::varInfo2Writable))
)

fun CodeBuilder.frameFull(instruction: Instruction, locals: List<VarInfo>, stack: List<VarInfo>) = frame(
    instruction,
    FullFrame(0, locals.map(::varInfo2Writable), stack.map(::varInfo2Writable))
)

private fun varInfo2Writable(varInfo: VarInfo) = when (varInfo) {
    IntInfo -> IntegerVariableInfo
    FloatInfo -> FloatVariableInfo
    is ObjInfo -> ObjectVariableInfo(varInfo.index)
}