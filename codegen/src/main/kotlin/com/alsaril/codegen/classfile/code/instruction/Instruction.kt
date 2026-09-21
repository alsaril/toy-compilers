package com.alsaril.codegen.classfile.code.instruction

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable
import com.alsaril.codegen.classfile.PrimitiveType
import com.alsaril.codegen.classfile.PrimitiveType.*
import com.alsaril.codegen.classfile.code.ClassPointer
import com.alsaril.codegen.classfile.code.DataPointer
import com.alsaril.codegen.classfile.code.FieldDescriptor
import com.alsaril.codegen.classfile.code.MethodDescriptor

sealed interface Instruction : Writable {
    fun stackEffects(): Pair<Int, Int> = 0 to 0
    fun locals(): Int? = null
}

data object nop : NoArgInstruction(0x00)

data object aconst_null : NoArgInstruction(0x01), PushesOne

data class iconst(val value: Int) : Instruction, PushesOne {
    init {
        require(value in Short.MIN_VALUE..Short.MAX_VALUE) {
            "$value is too big for iconst/bipush/sipush, ldc should be used"
        }
    }

    override fun ClassWriter.write() {
        when (value) {
            in -1..5 -> u1(0x03 + value)
            in Byte.MIN_VALUE..Byte.MAX_VALUE -> { u1(0x10); s1(value) }
            else -> { u1(0x11); s2(value) }
        }
    }
}

data class fconst(val value: Int) : OneMixedArgInstruction(0x0b, value), PushesOne {
    init {
        require(value in 0..2) { "$value is out of range for fconst, ldc should be used" }
    }
}

data class ldc(val index: Int) : Instruction, PushesOne {
    constructor(pointer: DataPointer) : this(pointer.index)

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

data class iload(override val index: Int) : LocalSlotInstruction(0x1a, 0x15), PushesOne
data class fload(override val index: Int) : LocalSlotInstruction(0x22, 0x17), PushesOne
data class aload(override val index: Int) : LocalSlotInstruction(0x2a, 0x19), PushesOne

data object iaload : NoArgInstruction(0x2e), PopsTwoPushesOne

data object baload : NoArgInstruction(0x33), PopsTwoPushesOne

data class istore(override val index: Int) : LocalSlotInstruction(0x3b, 0x36), PopsOne
data class fstore(override val index: Int) : LocalSlotInstruction(0x43, 0x38), PopsOne
data class astore(override val index: Int) : LocalSlotInstruction(0x4b, 0x3a), PopsOne

data object iastore : NoArgInstruction(0x4f), PopsThree

data object bastore : NoArgInstruction(0x54), PopsThree

data object iadd : NoArgInstruction(0x60), PopsTwoPushesOne

data object isub : NoArgInstruction(0x64), PopsTwoPushesOne

data object fadd : NoArgInstruction(0x62), PopsTwoPushesOne

data object fsub : NoArgInstruction(0x66), PopsTwoPushesOne

data object fmul : NoArgInstruction(0x6a), PopsTwoPushesOne

data object fdiv : NoArgInstruction(0x6e), PopsTwoPushesOne

data object fneg : NoArgInstruction(0x76), PopsOnePushesOne

data class iinc(override val index: Int, val delta: Int) : TouchesLocal {
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

data object dup : NoArgInstruction(0x59) {
    override fun stackEffects() = 1 to 2
}

data object dup2 : NoArgInstruction(0x5c) {
    override fun stackEffects() = 2 to 4
}

data object dup_x2 : NoArgInstruction(0x5b) {
    override fun stackEffects() = 3 to 4
}

data object ireturn : NoArgInstruction(0xac), PopsOne

data object freturn : NoArgInstruction(0xae), PopsOne

data object `return` : NoArgInstruction(0xb1)

data object athrow : NoArgInstruction(0xbf), PopsOne

data object ifeq : JumpInstruction(0x99), PopsOne

data object ifne : JumpInstruction(0x9a), PopsOne

data object ifge : JumpInstruction(0x9c), PopsOne

data object ifgt : JumpInstruction(0x9d), PopsOne

data object if_icmplt : JumpInstruction(0xa1), PopsTwo

data object if_icmpge : JumpInstruction(0xa2), PopsTwo

data object goto : JumpInstruction(0xa7)

data object ifnull : JumpInstruction(0xc6), PopsOne

data object ifnonnull : JumpInstruction(0xc7), PopsOne

data class getstatic(val index: Int, val slots: Int) : TwoBytesArgInstruction(0xb2, index) {
    constructor(field: FieldDescriptor) : this(field.index, field.slots)

    override fun stackEffects() = 0 to slots
}

data class invokevirtual(val index: Int, override val argSlots: Int, override val returnSlots: Int) :
    TwoBytesArgInstruction(0xb6, index), Invocation {
    constructor(method: MethodDescriptor) : this(method.index, method.argSlots, method.returnSlots)
}

data class invokespecial(val index: Int, override val argSlots: Int, override val returnSlots: Int) :
    TwoBytesArgInstruction(0xb7, index), Invocation {
    constructor(method: MethodDescriptor) : this(method.index, method.argSlots, method.returnSlots)
}

data class invokestatic(val index: Int, override val argSlots: Int, override val returnSlots: Int) :
    TwoBytesArgInstruction(0xb8, index), Invocation {
    constructor(method: MethodDescriptor) : this(method.index, method.argSlots, method.returnSlots)
}

data class invokeinterface(val index: Int, override val argSlots: Int, override val returnSlots: Int) :
    Invocation {
    constructor(method: MethodDescriptor) : this(method.index, method.argSlots, method.returnSlots)

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

data class new(val index: Int) : TwoBytesArgInstruction(0xbb, index), PushesOne {
    constructor(clazz: ClassPointer) : this(clazz.index)
}

data class newarray(val type: Int) : OneByteArgInstruction(0xbc, type), PopsOnePushesOne {
    constructor(type: PrimitiveType) : this(
        when (type) {
            BOOLEAN -> 4
            CHAR -> 5
            FLOAT -> 6
            DOUBLE -> 7
            BYTE -> 8
            SHORT -> 9
            INT -> 10
            LONG -> 11
            VOID -> throw IllegalArgumentException("an array cannot hold void")
        }
    )
}

data class checkcast(val index: Int) : TwoBytesArgInstruction(0xc0, index), PopsOnePushesOne {
    constructor(clazz: ClassPointer) : this(clazz.index)
}

data class instanceof(val index: Int) : TwoBytesArgInstruction(0xc1, index), PopsOnePushesOne {
    constructor(clazz: ClassPointer) : this(clazz.index)
}
