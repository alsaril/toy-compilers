package com.alsaril.codegen.classfile.code

fun CodeBuilder.goto(dest: Int? = null) = jumpTemplate(0xa7, dest)

fun CodeBuilder.ifeq(dest: Int? = null) = jumpTemplate(0x99, dest)

fun CodeBuilder.ifne(dest: Int? = null) = jumpTemplate(0x9a, dest)

fun CodeBuilder.if_icmplt(dest: Int? = null) = jumpTemplate(0xa1, dest)

fun CodeBuilder.if_icmpge(dest: Int? = null) = jumpTemplate(0xa2, dest)

// jump offsets are relative to the opcode, so an unknown dest is patched later
private fun CodeBuilder.jumpTemplate(opcode: Int, dest: Int?): (Int) -> Unit {
    val start = loc()
    b1(opcode)
    val pos = loc()
    if (dest != null) {
        b2(dest - start)
        return {}
    } else {
        b2(0) // placeholder
        return { target -> b2At(target - start, pos) }
    }
}
