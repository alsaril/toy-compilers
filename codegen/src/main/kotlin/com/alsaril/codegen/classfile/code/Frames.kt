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

fun CodeBuilder.frameSame() = frame(::sameFrame)

fun CodeBuilder.frameStack(varInfo: VarInfo) = frame { offsetDelta ->
    sameLocals1StackItem(offsetDelta, varInfo2Writable(varInfo))
}

fun CodeBuilder.frameAppend(vararg varInfos: VarInfo) = frame { offsetDelta ->
    AppendFrame(offsetDelta, varInfos.map(::varInfo2Writable))
}

fun CodeBuilder.frameFull(locals: List<VarInfo>, stack: List<VarInfo>) = frame { offsetDelta ->
    FullFrame(offsetDelta, locals.map(::varInfo2Writable), stack.map(::varInfo2Writable))
}

private fun varInfo2Writable(varInfo: VarInfo) = when (varInfo) {
    IntInfo -> IntegerVariableInfo
    FloatInfo -> FloatVariableInfo
    is ObjInfo -> ObjectVariableInfo(varInfo.index)
}