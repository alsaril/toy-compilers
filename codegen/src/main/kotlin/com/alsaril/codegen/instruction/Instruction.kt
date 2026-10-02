@file:MustUseReturnValues

package com.alsaril.codegen.instruction

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable
import com.alsaril.codegen.classfile.ArrayType
import com.alsaril.codegen.classfile.PrimitiveType
import com.alsaril.codegen.classfile.parseType
import com.alsaril.codegen.constantpool.ClassPointer
import com.alsaril.codegen.constantpool.DataPointer
import com.alsaril.codegen.constantpool.FieldDescriptor
import com.alsaril.codegen.constantpool.MethodDescriptor
import com.alsaril.codegen.verification.*
import com.alsaril.codegen.verification.PrimitiveType.*
import com.alsaril.codegen.classfile.ReferenceType as ClassType

sealed interface Instruction : Writable {
    fun stackEffect(): StackEffect = StackEffect.NONE
    fun localEffects(): List<LocalEffect> = emptyList()
}

data object nop : NoArgInstruction(0x00)

data object aconst_null : NoArgInstruction(0x01) {
    override fun stackEffect() = gives(NULL)
}

data class iconst(val value: Int) : Instruction {
    init {
        require(value in Short.MIN_VALUE..Short.MAX_VALUE) {
            "$value is too big for iconst/bipush/sipush, ldc should be used"
        }
    }

    override fun stackEffect() = gives(INTEGER)

    override fun ClassWriter.write() {
        when (value) {
            in -1..5 -> u1(0x03 + value)
            in Byte.MIN_VALUE..Byte.MAX_VALUE -> {
                u1(0x10); s1(value)
            }

            else -> {
                u1(0x11); s2(value)
            }
        }
    }
}

data class lconst(val value: Int) : OneMixedArgInstruction(0x09, value) {
    init {
        require(value in 0..1) { "$value is out of range for lconst, ldc2_w should be used" }
    }

    override fun stackEffect() = gives(LONG)
}

data class fconst(val value: Int) : OneMixedArgInstruction(0x0b, value) {
    init {
        require(value in 0..2) { "$value is out of range for fconst, ldc should be used" }
    }

    override fun stackEffect() = gives(FLOAT)
}

data class ldc(val index: Int, val type: VerificationType) : Instruction {
    constructor(pointer: DataPointer) : this(pointer.index, pointer.type)

    init {
        require(index in 0..0xffff) { "$index does not fit a u2" }
        require(type.slots == 1) { "ldc loads a one slot constant, but $type takes ${type.slots}, ldc2_w should be used" }
    }

    override fun stackEffect() = gives(type)

    override fun ClassWriter.write() {
        if (index < 0x100) {
            u1(0x12); u1(index)
        } else {
            u1(0x13); u2(index)
        }
    }
}

data class ldc2_w(val index: Int, val type: VerificationType) : TwoBytesArgInstruction(0x14, index) {
    constructor(pointer: DataPointer) : this(pointer.index, pointer.type)

    init {
        require(type.slots == 2) { "ldc2_w loads a two slot constant, but $type takes ${type.slots}, ldc should be used" }
    }

    override fun stackEffect() = gives(type)
}

data class iload(override val index: Int) : LocalSlotInstruction(0x1a, 0x15) {
    override fun stackEffect() = gives(INTEGER)
    override fun localEffects() = listOf(Read(index, INTEGER))
}

data class fload(override val index: Int) : LocalSlotInstruction(0x22, 0x17) {
    override fun stackEffect() = gives(FLOAT)
    override fun localEffects() = listOf(Read(index, FLOAT))
}

data class aload(override val index: Int) : LocalSlotInstruction(0x2a, 0x19), DynamicInstruction

data object iaload : NoArgInstruction(0x2e) {
    override fun stackEffect() = takes(ReferenceType("[I"), INTEGER) gives INTEGER
}

data object aaload : NoArgInstruction(0x32), DynamicInstruction

private val byteOrBooleanArray = OneOf(ReferenceType("[B"), ReferenceType("[Z"))

data object baload : NoArgInstruction(0x33) {
    override fun stackEffect() = takes(byteOrBooleanArray, OfType(INTEGER)) gives INTEGER
}

data class istore(override val index: Int) : LocalSlotInstruction(0x3b, 0x36) {
    override fun stackEffect() = takes(INTEGER)
    override fun localEffects() = listOf(Write(index, INTEGER))
}

data class lstore(override val index: Int) : LocalSlotInstruction(0x3f, 0x37) {
    override fun stackEffect() = takes(LONG)
    override fun localEffects() = listOf(Write(index, LONG))
}

data class fstore(override val index: Int) : LocalSlotInstruction(0x43, 0x38) {
    override fun stackEffect() = takes(FLOAT)
    override fun localEffects() = listOf(Write(index, FLOAT))
}

data class astore(override val index: Int) : LocalSlotInstruction(0x4b, 0x3a), DynamicInstruction

data object iastore : NoArgInstruction(0x4f) {
    override fun stackEffect() = takes(ReferenceType("[I"), INTEGER, INTEGER)
}

data object aastore : NoArgInstruction(0x53), DynamicInstruction

data object bastore : NoArgInstruction(0x54) {
    override fun stackEffect() = takes(byteOrBooleanArray, OfType(INTEGER), OfType(INTEGER))
}

data object iadd : NoArgInstruction(0x60) {
    override fun stackEffect() = takes(INTEGER, INTEGER) gives INTEGER
}

data object isub : NoArgInstruction(0x64) {
    override fun stackEffect() = takes(INTEGER, INTEGER) gives INTEGER
}

data object fadd : NoArgInstruction(0x62) {
    override fun stackEffect() = takes(FLOAT, FLOAT) gives FLOAT
}

data object fsub : NoArgInstruction(0x66) {
    override fun stackEffect() = takes(FLOAT, FLOAT) gives FLOAT
}

data object fmul : NoArgInstruction(0x6a) {
    override fun stackEffect() = takes(FLOAT, FLOAT) gives FLOAT
}

data object fdiv : NoArgInstruction(0x6e) {
    override fun stackEffect() = takes(FLOAT, FLOAT) gives FLOAT
}

data object fneg : NoArgInstruction(0x76) {
    override fun stackEffect() = takes(FLOAT) gives FLOAT
}

data class iinc(val index: Int, val delta: Int) : Instruction {
    init {
        require(index in 0..0xffff) { "$index does not fit a u2" }
        require(delta in Short.MIN_VALUE..Short.MAX_VALUE) { "$delta does not fit an s2" }
    }

    override fun localEffects() = listOf(Read(index, INTEGER), Write(index, INTEGER))

    override fun ClassWriter.write() {
        if (index <= 0xff && delta in Byte.MIN_VALUE..Byte.MAX_VALUE) {
            u1(0x84); u1(index); s1(delta)
        } else {
            u1(0xc4); u1(0x84); u2(index); s2(delta)
        }
    }
}

data object pop : NoArgInstruction(0x57), DynamicInstruction

data object pop2 : NoArgInstruction(0x58), DynamicInstruction

data object dup : NoArgInstruction(0x59), DynamicInstruction

data object dup_x1 : NoArgInstruction(0x5a), DynamicInstruction

data object dup_x2 : NoArgInstruction(0x5b), DynamicInstruction

data object dup2 : NoArgInstruction(0x5c), DynamicInstruction

data object ireturn : NoArgInstruction(0xac) {
    override fun stackEffect() = takes(INTEGER)
}

data object freturn : NoArgInstruction(0xae) {
    override fun stackEffect() = takes(FLOAT)
}

data object areturn : NoArgInstruction(0xb0) {
    override fun stackEffect() = takes(AnyReference)
}

data object `return` : NoArgInstruction(0xb1)

data object athrow : NoArgInstruction(0xbf) {
    override fun stackEffect() = takes(AnyReference)
}

data object ifeq : JumpInstruction(0x99) {
    override fun stackEffect() = takes(INTEGER)
}

data object ifne : JumpInstruction(0x9a) {
    override fun stackEffect() = takes(INTEGER)
}

data object ifge : JumpInstruction(0x9c) {
    override fun stackEffect() = takes(INTEGER)
}

data object ifgt : JumpInstruction(0x9d) {
    override fun stackEffect() = takes(INTEGER)
}

data object if_icmplt : JumpInstruction(0xa1) {
    override fun stackEffect() = takes(INTEGER, INTEGER)
}

data object if_icmpge : JumpInstruction(0xa2) {
    override fun stackEffect() = takes(INTEGER, INTEGER)
}

data object if_acmpeq : JumpInstruction(0xa5) {
    override fun stackEffect() = takes(AnyReference, AnyReference)
}

data object if_acmpne : JumpInstruction(0xa6) {
    override fun stackEffect() = takes(AnyReference, AnyReference)
}

data object goto : JumpInstruction(0xa7)

data object ifnull : JumpInstruction(0xc6) {
    override fun stackEffect() = takes(AnyReference)
}

data object ifnonnull : JumpInstruction(0xc7) {
    override fun stackEffect() = takes(AnyReference)
}

data class getstatic(val index: Int, val type: VerificationType) : TwoBytesArgInstruction(0xb2, index) {
    constructor(field: FieldDescriptor) : this(field.index, field.type)

    override fun stackEffect() = gives(type)
}

data class getfield(val index: Int, val ownerType: VerificationType, val type: VerificationType) :
    TwoBytesArgInstruction(0xb4, index) {
    constructor(field: FieldDescriptor) : this(field.index, field.ownerType, field.type)

    override fun stackEffect() = takes(ownerType) gives type
}

data class putfield(val index: Int, val ownerType: VerificationType, val type: VerificationType) :
    TwoBytesArgInstruction(0xb5, index) {
    constructor(field: FieldDescriptor) : this(field.index, field.ownerType, field.type)

    override fun stackEffect() = takes(ownerType, type)
}

data class invokevirtual(
    val index: Int,
    override val args: List<VerificationType>,
    override val returnType: VerificationType,
) : TwoBytesArgInstruction(0xb6, index), Invocation {
    constructor(method: MethodDescriptor) : this(method.index, method.args, method.returnType)
}

data class invokespecial(
    val index: Int,
    override val args: List<VerificationType>,
    override val returnType: VerificationType,
    val constructorFor: VerificationType?,
) : TwoBytesArgInstruction(0xb7, index), Invocation {
    constructor(method: MethodDescriptor) : this(method.index, method.args, method.returnType, method.constructorFor)
}

data class invokestatic(
    val index: Int,
    override val args: List<VerificationType>,
    override val returnType: VerificationType,
) : TwoBytesArgInstruction(0xb8, index), Invocation {
    constructor(method: MethodDescriptor) : this(method.index, method.args, method.returnType)
}

data class invokeinterface(
    val index: Int,
    override val args: List<VerificationType>,
    override val returnType: VerificationType,
) : Invocation {
    constructor(method: MethodDescriptor) : this(method.index, method.args, method.returnType)

    init {
        require(index in 0..0xffff) { "$index does not fit a u2" }
    }

    override fun ClassWriter.write() {
        u1(0xb9)
        u2(index)
        u1(args.sumOf { it.slots })
        u1(0)
    }
}

data class invokedynamic(
    val index: Int,
    override val args: List<VerificationType>,
    override val returnType: VerificationType,
) : Invocation {
    constructor(method: MethodDescriptor) : this(method.index, method.args, method.returnType)

    init {
        require(index in 0..0xffff) { "$index does not fit a u2" }
    }

    override fun ClassWriter.write() {
        u1(0xba)
        u2(index)
        u1(0)
        u1(0)
    }
}

data class new(val index: Int) : TwoBytesArgInstruction(0xbb, index), DynamicInstruction {
    constructor(clazz: ClassPointer) : this(clazz.index)
}

data class newarray(val type: Int, val descriptor: String) : OneByteArgInstruction(0xbc, type) {
    constructor(type: PrimitiveType) : this(serialize(type), ArrayType(type).descriptor)

    companion object {
        private fun serialize(type: PrimitiveType) = when (type) {
            PrimitiveType.BOOLEAN -> 4
            PrimitiveType.CHAR -> 5
            PrimitiveType.FLOAT -> 6
            PrimitiveType.DOUBLE -> 7
            PrimitiveType.BYTE -> 8
            PrimitiveType.SHORT -> 9
            PrimitiveType.INTEGER -> 10
            PrimitiveType.LONG -> 11
            PrimitiveType.VOID -> throw IllegalArgumentException("an array cannot hold void")
        }
    }

    override fun stackEffect() = takes(INTEGER) gives ReferenceType(descriptor)
}

data class anewarray(val index: Int, val clazz: String) : TwoBytesArgInstruction(0xbd, index) {
    constructor(clazz: ClassPointer) : this(clazz.index, clazz.name)

    private val element = if (clazz.startsWith('[')) parseType(clazz) else ClassType(clazz)

    override fun stackEffect() = takes(INTEGER) gives ArrayType(element).verificationType
}

data class checkcast(val index: Int, val clazz: String) : TwoBytesArgInstruction(0xc0, index) {
    constructor(clazz: ClassPointer) : this(clazz.index, clazz.name)

    override fun stackEffect() = takes(AnyReference) gives ReferenceType(clazz)
}

data class instanceof(val index: Int) : TwoBytesArgInstruction(0xc1, index) {
    constructor(clazz: ClassPointer) : this(clazz.index)

    override fun stackEffect() = takes(AnyReference) gives INTEGER
}
