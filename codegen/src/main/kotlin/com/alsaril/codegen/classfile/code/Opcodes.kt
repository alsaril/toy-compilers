package com.alsaril.codegen.classfile.code

fun CodeBuilder.nop() {
    b1(0x00)
}

// const
fun CodeBuilder.aconst_null() {
    b1(0x01)
}

fun CodeBuilder.iconst(value: Int) {
    if (value >= -1 && value <= 5) {
        b1(0x03 + value) // iconst
    } else if (value in Byte.MIN_VALUE..Byte.MAX_VALUE) {
        b1(0x10) // bipush
        b1(value)
    } else if (value in Short.MIN_VALUE..Short.MAX_VALUE) {
        b1(0x11) // sipush
        b2(value)
    } else {
        throw IllegalArgumentException("The value is too big for iconst/bipush/sipush, ldc should be used")
    }
}

// load
fun CodeBuilder.iload(index: Int) {
    instructionFamily(index, 0x1a, 0x15)
}

fun CodeBuilder.aload(index: Int) {
    instructionFamily(index, 0x2a, 0x19)
}

fun CodeBuilder.baload() {
    b1(0x33)
}

// store
fun CodeBuilder.istore(index: Int) {
    instructionFamily(index, 0x3b, 0x36)
}

fun CodeBuilder.astore(index: Int) {
    instructionFamily(index, 0x4b, 0x3a)
}

fun CodeBuilder.bastore() {
    b1(0x54)
}

// math
fun CodeBuilder.iadd() {
    b1(0x60)
}

fun CodeBuilder.isub() {
    b1(0x64)
}

fun CodeBuilder.iinc(index: Int, const: Int) {
    if (index < 0x100 && const in Byte.MIN_VALUE..Byte.MAX_VALUE) {
        b1(0x84)
        b1(index)
        b1(const)
    } else { // wide
        require(const in Short.MIN_VALUE..Short.MAX_VALUE)
        b1(0xc4)
        b1(0x84)
        b2(index)
        b2(const)
    }
}

// stack
fun CodeBuilder.dup() {
    b1(0x59)
}

fun CodeBuilder.dup2() {
    b1(0x5c)
}

// return
fun CodeBuilder.ireturn() {
    b1(0xac)
}

fun CodeBuilder.`return`() {
    b1(0xb1)
}

fun CodeBuilder.athrow() {
    b1(0xbf)
}

private fun CodeBuilder.instructionFamily(index: Int, short: Int, long: Int, limit: Int = 4) {
    if (index < limit) {
        b1(short + index)
    } else if (index < 0x100) {
        b1(long)
        b1(index)
    } else {
        throw NotImplementedError()
    }
}
