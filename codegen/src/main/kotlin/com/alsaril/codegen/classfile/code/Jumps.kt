package com.alsaril.codegen.classfile.code

fun CodeBuilder.ifeq(dest: Label? = null) = jumpTemplate(IfEq, dest)

fun CodeBuilder.ifne(dest: Label? = null) = jumpTemplate(IfNe, dest)

fun CodeBuilder.ifge(dest: Label? = null) = jumpTemplate(IfGe, dest)

fun CodeBuilder.ifgt(dest: Label? = null) = jumpTemplate(IfGt, dest)

fun CodeBuilder.if_icmplt(dest: Label? = null) = jumpTemplate(IfICmpLt, dest)

fun CodeBuilder.if_icmpge(dest: Label? = null) = jumpTemplate(IfICmpGe, dest)

fun CodeBuilder.goto(dest: Label? = null) = jumpTemplate(Goto, dest)

fun CodeBuilder.ifnull(dest: Label? = null) = jumpTemplate(IfNull, dest)

fun CodeBuilder.ifnotnull(dest: Label? = null) = jumpTemplate(IfNotNull, dest)

private fun CodeBuilder.jumpTemplate(jump: Instruction, dest: Label?): Label {
    val result = add(jump)
    dest?.let { link(result, dest) }
    return result
}
