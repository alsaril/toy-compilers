package com.alsaril.codegen.classfile.code

fun CodeBuilder.nop() {
    u1(0x00)
}

// const
fun CodeBuilder.aconst_null() {
    u1(0x01)
}

fun CodeBuilder.iconst(value: Int) {
    if (value >= -1 && value <= 5) {
        u1(0x03 + value) // iconst
    } else if (value in Byte.MIN_VALUE..Byte.MAX_VALUE) {
        u1(0x10) // bipush
        s1(value)
    } else if (value in Short.MIN_VALUE..Short.MAX_VALUE) {
        u1(0x11) // sipush
        s2(value)
    } else {
        throw IllegalArgumentException("The value is too big for iconst/bipush/sipush, ldc should be used")
    }
}

fun CodeBuilder.fconst(value: Int) {
    if (value >= 0 && value <= 2) {
        u1(0xb + value) // fconst
    } else {
        throw IllegalArgumentException("The value is out of range for fconst, ldc should be used")
    }
}

fun CodeBuilder.ldc(pointer: DataPointer) {
    val index = pointer.index
    if (index < 0x100) {
        u1(0x12) // ldc
        u1(index)
    } else {
        u1(0x13) // ldc_w
        u2(index)
    }
}

// load
fun CodeBuilder.iload(index: Int) {
    instructionFamily(index, 0x1a, 0x15)
}

fun CodeBuilder.fload(index: Int) {
    instructionFamily(index, 0x22, 0x17)
}

fun CodeBuilder.aload(index: Int) {
    instructionFamily(index, 0x2a, 0x19)
}

fun CodeBuilder.iaload() {
    u1(0x2e)
}

fun CodeBuilder.baload() {
    u1(0x33)
}

// store
fun CodeBuilder.istore(index: Int) {
    instructionFamily(index, 0x3b, 0x36)
}

fun CodeBuilder.fstore(index: Int) {
    instructionFamily(index, 0x43, 0x38)
}

fun CodeBuilder.astore(index: Int) {
    instructionFamily(index, 0x4b, 0x3a)
}

fun CodeBuilder.iastore() {
    u1(0x4f)
}

fun CodeBuilder.bastore() {
    u1(0x54)
}

// math
fun CodeBuilder.iadd() {
    u1(0x60)
}

fun CodeBuilder.isub() {
    u1(0x64)
}

fun CodeBuilder.fadd() {
    u1(0x62)
}

fun CodeBuilder.fsub() {
    u1(0x66)
}

fun CodeBuilder.fmul() {
    u1(0x6a)
}

fun CodeBuilder.fdiv() {
    u1(0x6e)
}

fun CodeBuilder.iinc(index: Int, const: Int) {
    if (index < 0x100 && const in Byte.MIN_VALUE..Byte.MAX_VALUE) {
        u1(0x84)
        u1(index)
        s1(const)
    } else { // wide
        u1(0xc4)
        u1(0x84)
        u2(index)
        s2(const)
    }
}

// stack
fun CodeBuilder.dup() {
    u1(0x59)
}

fun CodeBuilder.dup2() {
    u1(0x5c)
}

fun CodeBuilder.dup_x2() {
    u1(0x5b)
}

// return
fun CodeBuilder.ireturn() {
    u1(0xac)
}

fun CodeBuilder.freturn() {
    u1(0xae)
}

fun CodeBuilder.`return`() {
    u1(0xb1)
}

fun CodeBuilder.athrow() {
    u1(0xbf)
}

private fun CodeBuilder.instructionFamily(index: Int, short: Int, long: Int, limit: Int = 4) {
    if (index < limit) {
        u1(short + index)
    } else if (index < 0x100) {
        u1(long)
        u1(index)
    } else {
        throw NotImplementedError()
    }
}
