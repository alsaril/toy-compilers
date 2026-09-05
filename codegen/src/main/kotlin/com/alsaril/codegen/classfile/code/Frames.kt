package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.classfile.attributes.AppendFrame
import com.alsaril.codegen.classfile.attributes.ObjectVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.IntegerVariableInfo
import com.alsaril.codegen.classfile.attributes.sameFrame

sealed interface VarInfo

data object IntInfo : VarInfo

data class ObjInfo(val descriptor: String) : VarInfo

fun CodeBuilder.frameSame() = frame(::sameFrame)

fun CodeBuilder.frameAppend(vararg varInfos: VarInfo) = frame { offsetDelta ->
    val locals = varInfos.map {
        when (it) {
            IntInfo -> IntegerVariableInfo
            is ObjInfo -> ObjectVariableInfo(cp.putClass(it.descriptor))
        }
    }
    AppendFrame(offsetDelta, locals)
}
