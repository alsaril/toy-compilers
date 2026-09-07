package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.classfile.attributes.StackMapFrame
import com.alsaril.codegen.constantpool.UpdatableConstantPool

const val THIS_CLASS = "This"
const val PARENT_CLASS = "Parent"

fun builder(cp: UpdatableConstantPool = UpdatableConstantPool()) = CodeBuilder(cp, THIS_CLASS, PARENT_CLASS)

fun bytecode(block: CodeBuilder.() -> Unit): ByteArray = builder().apply(block).build().bytecode()

fun frames(block: CodeBuilder.() -> Unit): List<StackMapFrame> = builder().apply(block).build().frames

fun handlers(block: CodeBuilder.() -> Unit): List<ExceptionHandler> =
    builder().apply(block).build().exceptionHandlers
