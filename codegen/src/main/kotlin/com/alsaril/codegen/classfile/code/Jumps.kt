package com.alsaril.codegen.classfile.code

fun CodeBuilder.ifeq(dest: Int? = null) = jumpTemplate(0x99, dest)

fun CodeBuilder.ifne(dest: Int? = null) = jumpTemplate(0x9a, dest)

fun CodeBuilder.ifge(dest: Int? = null) = jumpTemplate(0x9c, dest)

fun CodeBuilder.ifgt(dest: Int? = null) = jumpTemplate(0x9d, dest)

fun CodeBuilder.if_icmplt(dest: Int? = null) = jumpTemplate(0xa1, dest)

fun CodeBuilder.if_icmpge(dest: Int? = null) = jumpTemplate(0xa2, dest)

fun CodeBuilder.goto(dest: Int? = null) = jumpTemplate(0xa7, dest)

fun CodeBuilder.ifnull(dest: Int? = null) = jumpTemplate(0xc6, dest)

fun CodeBuilder.ifnotnull(dest: Int? = null) = jumpTemplate(0xc7, dest)

// jump offsets are relative to the opcode, so an unknown dest is patched later
private fun CodeBuilder.jumpTemplate(opcode: Int, dest: Int?): (Int) -> Unit {
    val start = loc()
    u1(opcode)
    val pos = loc()
    if (dest != null) {
        s2(dest - start)
        return {}
    } else {
        s2(0) // placeholder
        return { target -> s2At(target - start, pos) }
    }
}
