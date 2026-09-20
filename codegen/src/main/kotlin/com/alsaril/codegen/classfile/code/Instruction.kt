package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable

sealed interface Instruction : Writable {
    fun stackEffects(): Pair<Int, Int> = 0 to 0
    fun locals(): Int? = null
}

internal interface PushesOne : Instruction {
    override fun stackEffects() = 0 to 1
}

internal interface PopsOne : Instruction {
    override fun stackEffects() = 1 to 0
}

internal interface PopsTwo : Instruction {
    override fun stackEffects() = 2 to 0
}

internal interface PopsThree : Instruction {
    override fun stackEffects() = 3 to 0
}

internal interface PopsOnePushesOne : Instruction {
    override fun stackEffects() = 1 to 1
}

internal interface PopsTwoPushesOne : Instruction {
    override fun stackEffects() = 2 to 1
}

internal interface Invocation : Instruction {
    val argSlots: Int
    val returnSlots: Int

    override fun stackEffects() = argSlots to returnSlots
}

internal interface TouchesLocal : Instruction {
    val index: Int

    override fun locals() = index
}

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
        require(arg in 0..0xff) { "$arg does not fit a u1" }
    }

    override fun ClassWriter.write() {
        u1(code)
        u1(arg)
    }
}

abstract class OneSignedByteArgInstruction(val code: Int, val arg: Int) : Instruction {
    init {
        require(arg in Byte.MIN_VALUE..Byte.MAX_VALUE) { "$arg does not fit an s1" }
    }

    override fun ClassWriter.write() {
        u1(code)
        s1(arg)
    }
}

abstract class TwoBytesArgInstruction(val code: Int, val arg: Int) : Instruction {
    init {
        require(arg in 0..0xffff) { "$arg does not fit a u2" }
    }

    override fun ClassWriter.write() {
        u1(code)
        u2(arg)
    }
}

abstract class TwoSignedBytesArgInstruction(val code: Int, val arg: Int) : Instruction {
    init {
        require(arg in Short.MIN_VALUE..Short.MAX_VALUE) { "$arg does not fit an s2" }
    }

    override fun ClassWriter.write() {
        u1(code)
        s2(arg)
    }
}

abstract class WideTwoBytesArgInstruction(val code: Int, val arg: Int) : Instruction {
    init {
        require(arg in 0..0xffff) { "$arg does not fit a u2" }
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

internal data object AConstNull : NoArgInstruction(0x01), PushesOne

internal data class IConst(val value: Int) : OneMixedArgInstruction(0x03, value), PushesOne {
    init {
        require(value in -1..5) { "$value is out of range for iconst" }
    }
}

internal data class BIPush(val value: Int) : OneSignedByteArgInstruction(0x10, value), PushesOne

internal data class SIPush(val value: Int) : TwoSignedBytesArgInstruction(0x11, value), PushesOne

internal data class FConst(val value: Int) : OneMixedArgInstruction(0x0b, value), PushesOne {
    init {
        require(value in 0..2) { "$value is out of range for fconst" }
    }
}

internal data class Ldc(val index: Int) : OneByteArgInstruction(0x12, index), PushesOne

internal data class LdcW(val index: Int) : TwoBytesArgInstruction(0x13, index), PushesOne

internal data class ILoad(override val index: Int) : OneMixedArgInstruction(0x1a, index), PushesOne, TouchesLocal {
    init {
        require(index in 0..3) { "slot $index is out of range for the compact form" }
    }
}

internal data class ILoadN(override val index: Int) : OneByteArgInstruction(0x15, index), PushesOne, TouchesLocal

internal data class ILoadW(override val index: Int) : WideTwoBytesArgInstruction(0x15, index), PushesOne, TouchesLocal

internal data class FLoad(override val index: Int) : OneMixedArgInstruction(0x22, index), PushesOne, TouchesLocal {
    init {
        require(index in 0..3) { "slot $index is out of range for the compact form" }
    }
}

internal data class FLoadN(override val index: Int) : OneByteArgInstruction(0x17, index), PushesOne, TouchesLocal

internal data class FLoadW(override val index: Int) : WideTwoBytesArgInstruction(0x17, index), PushesOne, TouchesLocal

internal data class ALoad(override val index: Int) : OneMixedArgInstruction(0x2a, index), PushesOne, TouchesLocal {
    init {
        require(index in 0..3) { "slot $index is out of range for the compact form" }
    }
}

internal data class ALoadN(override val index: Int) : OneByteArgInstruction(0x19, index), PushesOne, TouchesLocal

internal data class ALoadW(override val index: Int) : WideTwoBytesArgInstruction(0x19, index), PushesOne, TouchesLocal

internal data object IALoad : NoArgInstruction(0x2e), PopsTwoPushesOne

internal data object BALoad : NoArgInstruction(0x33), PopsTwoPushesOne

internal data class IStore(override val index: Int) : OneMixedArgInstruction(0x3b, index), PopsOne, TouchesLocal {
    init {
        require(index in 0..3) { "slot $index is out of range for the compact form" }
    }
}

internal data class IStoreN(override val index: Int) : OneByteArgInstruction(0x36, index), PopsOne, TouchesLocal

internal data class IStoreW(override val index: Int) : WideTwoBytesArgInstruction(0x36, index), PopsOne, TouchesLocal

internal data class FStore(override val index: Int) : OneMixedArgInstruction(0x43, index), PopsOne, TouchesLocal {
    init {
        require(index in 0..3) { "slot $index is out of range for the compact form" }
    }
}

internal data class FStoreN(override val index: Int) : OneByteArgInstruction(0x38, index), PopsOne, TouchesLocal

internal data class FStoreW(override val index: Int) : WideTwoBytesArgInstruction(0x38, index), PopsOne, TouchesLocal

internal data class AStore(override val index: Int) : OneMixedArgInstruction(0x4b, index), PopsOne, TouchesLocal {
    init {
        require(index in 0..3) { "slot $index is out of range for the compact form" }
    }
}

internal data class AStoreN(override val index: Int) : OneByteArgInstruction(0x3a, index), PopsOne, TouchesLocal

internal data class AStoreW(override val index: Int) : WideTwoBytesArgInstruction(0x3a, index), PopsOne, TouchesLocal

internal data object IAStore : NoArgInstruction(0x4f), PopsThree

internal data object BAStore : NoArgInstruction(0x54), PopsThree

internal data object IAdd : NoArgInstruction(0x60), PopsTwoPushesOne

internal data object ISub : NoArgInstruction(0x64), PopsTwoPushesOne

internal data object FAdd : NoArgInstruction(0x62), PopsTwoPushesOne

internal data object FSub : NoArgInstruction(0x66), PopsTwoPushesOne

internal data object FMul : NoArgInstruction(0x6a), PopsTwoPushesOne

internal data object FDiv : NoArgInstruction(0x6e), PopsTwoPushesOne

internal data object FNeg : NoArgInstruction(0x76), PopsOnePushesOne

internal data class IInc(override val index: Int, val delta: Int) : TouchesLocal {
    init {
        require(index in 0..0xff) { "$index does not fit a u1" }
        require(delta in Byte.MIN_VALUE..Byte.MAX_VALUE) { "$delta does not fit an s1" }
    }

    override fun ClassWriter.write() {
        u1(0x84)
        u1(index)
        s1(delta)
    }
}

internal data class IIncW(override val index: Int, val delta: Int) : TouchesLocal {
    init {
        require(index in 0..0xffff) { "$index does not fit a u2" }
        require(delta in Short.MIN_VALUE..Short.MAX_VALUE) { "$delta does not fit an s2" }
    }

    override fun ClassWriter.write() {
        u1(0xc4)
        u1(0x84)
        u2(index)
        s2(delta)
    }
}

internal data object Dup : NoArgInstruction(0x59) {
    override fun stackEffects() = 1 to 2
}

internal data object Dup2 : NoArgInstruction(0x5c) {
    override fun stackEffects() = 2 to 4
}

internal data object DupX2 : NoArgInstruction(0x5b) {
    override fun stackEffects() = 3 to 4
}

internal data object IReturn : NoArgInstruction(0xac), PopsOne

internal data object FReturn : NoArgInstruction(0xae), PopsOne

internal data object Return : NoArgInstruction(0xb1)

internal data object AThrow : NoArgInstruction(0xbf), PopsOne

internal data object IfEq : JumpTemplateInstruction(0x99), PopsOne

internal data object IfNe : JumpTemplateInstruction(0x9a), PopsOne

internal data object IfGe : JumpTemplateInstruction(0x9c), PopsOne

internal data object IfGt : JumpTemplateInstruction(0x9d), PopsOne

internal data object IfICmpLt : JumpTemplateInstruction(0xa1), PopsTwo

internal data object IfICmpGe : JumpTemplateInstruction(0xa2), PopsTwo

internal data object Goto : JumpTemplateInstruction(0xa7)

internal data object IfNull : JumpTemplateInstruction(0xc6), PopsOne

internal data object IfNotNull : JumpTemplateInstruction(0xc7), PopsOne

internal data class GetStatic(val index: Int) : TwoBytesArgInstruction(0xb2, index), PushesOne

internal data class InvokeVirtual(val index: Int, override val argSlots: Int, override val returnSlots: Int) :
    TwoBytesArgInstruction(0xb6, index), Invocation

internal data class InvokeSpecial(val index: Int, override val argSlots: Int, override val returnSlots: Int) :
    TwoBytesArgInstruction(0xb7, index), Invocation

internal data class InvokeStatic(val index: Int, override val argSlots: Int, override val returnSlots: Int) :
    TwoBytesArgInstruction(0xb8, index), Invocation

internal data class InvokeInterface(val index: Int, override val argSlots: Int, override val returnSlots: Int) : Invocation {
    init {
        require(index in 0..0xffff) { "$index does not fit a u2" }
    }

    override fun ClassWriter.write() {
        u1(0xb9)
        u2(index)
        u1(argSlots)
        u1(0)
    }
}

internal data class New(val index: Int) : TwoBytesArgInstruction(0xbb, index), PushesOne

internal data class NewArray(val type: Int) : OneByteArgInstruction(0xbc, type), PopsOnePushesOne

internal data class CheckCast(val index: Int) : TwoBytesArgInstruction(0xc0, index), PopsOnePushesOne

internal data class InstanceOf(val index: Int) : TwoBytesArgInstruction(0xc1, index), PopsOnePushesOne