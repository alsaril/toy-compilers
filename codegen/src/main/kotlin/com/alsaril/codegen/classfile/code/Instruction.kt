package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable

sealed interface Instruction : Writable

abstract class NoArgInstruction(val code: Int) : Instruction {
    override fun ClassWriter.write() = u1(code)
}

abstract class OneMixedArgInstruction(val code: Int, val arg: Int) : Instruction {
    override fun ClassWriter.write() {
        u1(code + arg)
    }
}

abstract class OneByteArgInstruction(val code: Int, val arg: Int) : Instruction {
    init {
        require(arg in 0..0xff)
    }

    override fun ClassWriter.write() {
        u1(code)
        u1(arg)
    }
}

abstract class OneSignedByteArgInstruction(val code: Int, val arg: Int) : Instruction {
    init {
        require(arg in Byte.MIN_VALUE..Byte.MAX_VALUE)
    }

    override fun ClassWriter.write() {
        u1(code)
        s1(arg)
    }
}

abstract class TwoBytesArgInstruction(val code: Int, val arg: Int) : Instruction {
    init {
        require(arg in 0..0xffff)
    }

    override fun ClassWriter.write() {
        u1(code)
        u2(arg)
    }
}

abstract class TwoSignedBytesArgInstruction(val code: Int, val arg: Int) : Instruction {
    init {
        require(arg in Short.MIN_VALUE..Short.MAX_VALUE)
    }

    override fun ClassWriter.write() {
        u1(code)
        s2(arg)
    }
}

abstract class WideTwoBytesArgInstruction(val code: Int, val arg: Int) : Instruction {
    init {
        require(arg in 0..0xffff)
    }

    override fun ClassWriter.write() {
        u1(0xc4)
        u1(code)
        u2(arg)
    }
}

abstract class JumpTemplateInstruction(val code: Int) : Instruction {
    override fun ClassWriter.write() {
        u1(code)
        s2(0)
    }
}

internal data object Nop : NoArgInstruction(0x00)

internal data object AConstNull : NoArgInstruction(0x01)

internal data class IConst(val value: Int) : OneMixedArgInstruction(0x03, value) {
    init {
        require(value in -1..5)
    }
}

internal data class BIPush(val value: Int) : OneSignedByteArgInstruction(0x10, value)

internal data class SIPush(val value: Int) : TwoSignedBytesArgInstruction(0x11, value)

internal data class FConst(val value: Int) : OneMixedArgInstruction(0x0b, value) {
    init {
        require(value in 0..2)
    }
}

internal data class Ldc(val index: Int) : OneByteArgInstruction(0x12, index)

internal data class LdcW(val index: Int) : TwoBytesArgInstruction(0x13, index)

internal data class ILoad(val index: Int) : OneMixedArgInstruction(0x1a, index) {
    init {
        require(index in 0..3)
    }
}

internal data class ILoadN(val index: Int) : OneByteArgInstruction(0x15, index)

internal data class ILoadW(val index: Int) : WideTwoBytesArgInstruction(0x15, index)

internal data class FLoad(val index: Int) : OneMixedArgInstruction(0x22, index) {
    init {
        require(index in 0..3)
    }
}

internal data class FLoadN(val index: Int) : OneByteArgInstruction(0x17, index)

internal data class FLoadW(val index: Int) : WideTwoBytesArgInstruction(0x17, index)

internal data class ALoad(val index: Int) : OneMixedArgInstruction(0x2a, index) {
    init {
        require(index in 0..3)
    }
}

internal data class ALoadN(val index: Int) : OneByteArgInstruction(0x19, index)

internal data class ALoadW(val index: Int) : WideTwoBytesArgInstruction(0x19, index)

internal data object IALoad : NoArgInstruction(0x2e)

internal data object BALoad : NoArgInstruction(0x33)

internal data class IStore(val index: Int) : OneMixedArgInstruction(0x3b, index) {
    init {
        require(index in 0..3)
    }
}

internal data class IStoreN(val index: Int) : OneByteArgInstruction(0x36, index)

internal data class IStoreW(val index: Int) : WideTwoBytesArgInstruction(0x36, index)

internal data class FStore(val index: Int) : OneMixedArgInstruction(0x43, index) {
    init {
        require(index in 0..3)
    }
}

internal data class FStoreN(val index: Int) : OneByteArgInstruction(0x38, index)

internal data class FStoreW(val index: Int) : WideTwoBytesArgInstruction(0x38, index)

internal data class AStore(val index: Int) : OneMixedArgInstruction(0x4b, index) {
    init {
        require(index in 0..3)
    }
}

internal data class AStoreN(val index: Int) : OneByteArgInstruction(0x3a, index)

internal data class AStoreW(val index: Int) : WideTwoBytesArgInstruction(0x3a, index)

internal data object IAStore : NoArgInstruction(0x4f)

internal data object BAStore : NoArgInstruction(0x54)

internal data object IAdd : NoArgInstruction(0x60)
internal data object ISub : NoArgInstruction(0x64)

internal data object FAdd : NoArgInstruction(0x62)
internal data object FSub : NoArgInstruction(0x66)
internal data object FMul : NoArgInstruction(0x6a)
internal data object FDiv : NoArgInstruction(0x6e)
internal data object FNeg : NoArgInstruction(0x76)

internal data class IInc(val index: Int, val delta: Int) : Instruction {
    init {
        require(index in 0..0xff && delta in Byte.MIN_VALUE..Byte.MAX_VALUE)
    }

    override fun ClassWriter.write() {
        u1(0x84)
        u1(index)
        s1(delta)
    }
}

internal data class IIncW(val index: Int, val delta: Int) : Instruction {
    init {
        require(index in 0..0xffff && delta in Short.MIN_VALUE..Short.MAX_VALUE)
    }

    override fun ClassWriter.write() {
        u1(0xc4)
        u1(0x84)
        u2(index)
        s2(delta)
    }
}

internal data object Dup : NoArgInstruction(0x59)
internal data object Dup2 : NoArgInstruction(0x5c)
internal data object DupX2 : NoArgInstruction(0x5b)

internal data object IReturn : NoArgInstruction(0xac)
internal data object FReturn : NoArgInstruction(0xae)
internal data object Return : NoArgInstruction(0xb1)
internal data object AThrow : NoArgInstruction(0xbf)

internal data object IfEq : JumpTemplateInstruction(0x99)
internal data object IfNe : JumpTemplateInstruction(0x9a)
internal data object IfGe : JumpTemplateInstruction(0x9c)
internal data object IfGt : JumpTemplateInstruction(0x9d)

internal data object IfICmpLt : JumpTemplateInstruction(0xa1)
internal data object IfICmpGe : JumpTemplateInstruction(0xa2)

internal data object Goto : JumpTemplateInstruction(0xa7)

internal data object IfNull : JumpTemplateInstruction(0xc6)
internal data object IfNotNull : JumpTemplateInstruction(0xc7)

internal data class GetStatic(val index: Int) : TwoBytesArgInstruction(0xb2, index)
internal data class InvokeVirtual(val index: Int) : TwoBytesArgInstruction(0xb6, index)
internal data class InvokeSpecial(val index: Int) : TwoBytesArgInstruction(0xb7, index)
internal data class InvokeStatic(val index: Int) : TwoBytesArgInstruction(0xb8, index)
internal data class InvokeInterface(val index: Int, val count: Int) : Instruction {
    init {
        require(index in 0..0xffff)
    }

    override fun ClassWriter.write() {
        u1(0xb9)
        u2(index)
        u1(count)
        u1(0)
    }
}

internal data class New(val index: Int) : TwoBytesArgInstruction(0xbb, index)
internal data class NewArray(val type: Int) : OneByteArgInstruction(0xbc, type)

internal data class CheckCast(val index: Int) : TwoBytesArgInstruction(0xc0, index)
internal data class InstanceOf(val index: Int) : TwoBytesArgInstruction(0xc1, index)