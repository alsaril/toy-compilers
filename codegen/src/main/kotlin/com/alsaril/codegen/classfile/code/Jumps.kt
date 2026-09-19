package com.alsaril.codegen.classfile.code

fun CodeBuilder.ifeq(dest: Instruction? = null) = add(jumpTemplate(IfEq, dest))

fun CodeBuilder.ifne(dest: Instruction? = null) = add(jumpTemplate(IfNe, dest))

fun CodeBuilder.ifge(dest: Instruction? = null) = add(jumpTemplate(IfGe, dest))

fun CodeBuilder.ifgt(dest: Instruction? = null) = add(jumpTemplate(IfGt, dest))

fun CodeBuilder.if_icmplt(dest: Instruction? = null) = add(jumpTemplate(IfICmpLt, dest))

fun CodeBuilder.if_icmpge(dest: Instruction? = null) = add(jumpTemplate(IfICmpGe, dest))

fun CodeBuilder.goto(dest: Instruction? = null) = add(jumpTemplate(Goto, dest))

fun CodeBuilder.ifnull(dest: Instruction? = null) = add(jumpTemplate(IfNull, dest))

fun CodeBuilder.ifnotnull(dest: Instruction? = null) = add(jumpTemplate(IfNotNull, dest))

private fun CodeBuilder.jumpTemplate(jump: Instruction, dest: Instruction?): Instruction {
    dest?.let { link(jump, dest) }
    return jump
}
