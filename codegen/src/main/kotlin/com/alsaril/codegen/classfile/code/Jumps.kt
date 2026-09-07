package com.alsaril.codegen.classfile.code

fun CodeBuilder.goto(dest: Int? = null) = jumpTemplate(0xa7, dest)

fun CodeBuilder.ifeq(dest: Int? = null) = jumpTemplate(0x99, dest)

fun CodeBuilder.ifne(dest: Int? = null) = jumpTemplate(0x9a, dest)

fun CodeBuilder.ifge(dest: Int? = null) = jumpTemplate(0x9c, dest)

fun CodeBuilder.ifgt(dest: Int? = null) = jumpTemplate(0x9d, dest)

fun CodeBuilder.if_icmplt(dest: Int? = null) = jumpTemplate(0xa1, dest)

fun CodeBuilder.if_icmpge(dest: Int? = null) = jumpTemplate(0xa2, dest)

// jump offsets are relative to the opcode, so an unknown dest is patched later
private fun CodeBuilder.jumpTemplate(opcode: Int, dest: Int?): (Int) -> Unit {
    val start = loc()
    b1(opcode)
    val pos = loc()
    if (dest != null) {
        b2(offset(dest, start))
        return {}
    } else {
        b2(0) // placeholder
        return { target -> b2At(offset(target, start), pos) }
    }
}

private fun offset(target: Int, start: Int): Int {
    val offset = target - start
    require(offset in Short.MIN_VALUE..Short.MAX_VALUE) { "branch offset $offset is out of range" }
    return offset
}
