package com.alsaril.codegen.classfile.code.instruction

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable

sealed interface Instruction : Writable {
    fun stackEffects(): Pair<Int, Int> = 0 to 0
    fun locals(): Int? = null
}

data object Nop : NoArgInstruction(0x00)

data object AConstNull : NoArgInstruction(0x01), PushesOne

data class IConst(val value: Int) : OneMixedArgInstruction(0x03, value), PushesOne {
    init {
        require(value in -1..5) { "$value is out of range for iconst" }
    }
}

data class BIPush(val value: Int) : OneSignedByteArgInstruction(0x10, value), PushesOne

data class SIPush(val value: Int) : TwoSignedBytesArgInstruction(0x11, value), PushesOne

data class FConst(val value: Int) : OneMixedArgInstruction(0x0b, value), PushesOne {
    init {
        require(value in 0..2) { "$value is out of range for fconst" }
    }
}

data class ILoad(override val index: Int) : LocalSlotInstruction(0x1a, 0x15), PushesOne
data class FLoad(override val index: Int) : LocalSlotInstruction(0x22, 0x17), PushesOne
data class ALoad(override val index: Int) : LocalSlotInstruction(0x2a, 0x19), PushesOne
data class IStore(override val index: Int) : LocalSlotInstruction(0x3b, 0x36), PopsOne
data class FStore(override val index: Int) : LocalSlotInstruction(0x43, 0x38), PopsOne
data class AStore(override val index: Int) : LocalSlotInstruction(0x4b, 0x3a), PopsOne

data class Ldc(val index: Int) : Instruction, PushesOne {
    init {
        require(index in 0..0xffff) { "$index does not fit a u2" }
    }

    override fun ClassWriter.write() {
        if (index < 0x100) {
            u1(0x12); u1(index)
        } else {
            u1(0x13); u2(index)
        }
    }
}

data object IALoad : NoArgInstruction(0x2e), PopsTwoPushesOne

data object BALoad : NoArgInstruction(0x33), PopsTwoPushesOne

data object IAStore : NoArgInstruction(0x4f), PopsThree

data object BAStore : NoArgInstruction(0x54), PopsThree

data object IAdd : NoArgInstruction(0x60), PopsTwoPushesOne

data object ISub : NoArgInstruction(0x64), PopsTwoPushesOne

data object FAdd : NoArgInstruction(0x62), PopsTwoPushesOne

data object FSub : NoArgInstruction(0x66), PopsTwoPushesOne

data object FMul : NoArgInstruction(0x6a), PopsTwoPushesOne

data object FDiv : NoArgInstruction(0x6e), PopsTwoPushesOne

data object FNeg : NoArgInstruction(0x76), PopsOnePushesOne

data class IInc(override val index: Int, val delta: Int) : TouchesLocal {
    init {
        require(index in 0..0xffff) { "$index does not fit a u2" }
        require(delta in Short.MIN_VALUE..Short.MAX_VALUE) { "$delta does not fit an s2" }
    }

    override fun ClassWriter.write() {
        if (index <= 0xff && delta in Byte.MIN_VALUE..Byte.MAX_VALUE) {
            u1(0x84); u1(index); s1(delta)
        } else {
            u1(0xc4); u1(0x84); u2(index); s2(delta)
        }
    }
}

data object Dup : NoArgInstruction(0x59) {
    override fun stackEffects() = 1 to 2
}

data object Dup2 : NoArgInstruction(0x5c) {
    override fun stackEffects() = 2 to 4
}

data object DupX2 : NoArgInstruction(0x5b) {
    override fun stackEffects() = 3 to 4
}

data object IReturn : NoArgInstruction(0xac), PopsOne

data object FReturn : NoArgInstruction(0xae), PopsOne

data object Return : NoArgInstruction(0xb1)

data object AThrow : NoArgInstruction(0xbf), PopsOne

data object IfEq : JumpTemplateInstruction(0x99), PopsOne

data object IfNe : JumpTemplateInstruction(0x9a), PopsOne

data object IfGe : JumpTemplateInstruction(0x9c), PopsOne

data object IfGt : JumpTemplateInstruction(0x9d), PopsOne

data object IfICmpLt : JumpTemplateInstruction(0xa1), PopsTwo

data object IfICmpGe : JumpTemplateInstruction(0xa2), PopsTwo

data object Goto : JumpTemplateInstruction(0xa7)

data object IfNull : JumpTemplateInstruction(0xc6), PopsOne

data object IfNotNull : JumpTemplateInstruction(0xc7), PopsOne

data class GetStatic(val index: Int, val slots: Int) : TwoBytesArgInstruction(0xb2, index) {
    override fun stackEffects() = 0 to slots
}

data class InvokeVirtual(val index: Int, override val argSlots: Int, override val returnSlots: Int) :
    TwoBytesArgInstruction(0xb6, index), Invocation

data class InvokeSpecial(val index: Int, override val argSlots: Int, override val returnSlots: Int) :
    TwoBytesArgInstruction(0xb7, index), Invocation

data class InvokeStatic(val index: Int, override val argSlots: Int, override val returnSlots: Int) :
    TwoBytesArgInstruction(0xb8, index), Invocation

data class InvokeInterface(val index: Int, override val argSlots: Int, override val returnSlots: Int) :
    Invocation {
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

data class New(val index: Int) : TwoBytesArgInstruction(0xbb, index), PushesOne

data class NewArray(val type: Int) : OneByteArgInstruction(0xbc, type), PopsOnePushesOne

data class CheckCast(val index: Int) : TwoBytesArgInstruction(0xc0, index), PopsOnePushesOne

data class InstanceOf(val index: Int) : TwoBytesArgInstruction(0xc1, index), PopsOnePushesOne
