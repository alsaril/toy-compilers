package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.classfile.attributes.StackMapFrame
import com.alsaril.codegen.classfile.bytecode
import com.alsaril.codegen.constantpool.UpdatableConstantPool

const val THIS_CLASS = "This"
const val PARENT_CLASS = "Parent"

fun builder(cp: UpdatableConstantPool = UpdatableConstantPool()) = CodeBuilder(cp, THIS_CLASS, PARENT_CLASS)

fun bytecode(block: CodeBuilder.() -> Unit): ByteArray = builder().apply(block).build().bytecode()

fun frames(
    cp: UpdatableConstantPool = UpdatableConstantPool(),
    block: CodeBuilder.() -> Unit,
): List<StackMapFrame> = BytecodeSerializer.emit(builder(cp).apply(block).build()).second

fun framesOf(fragment: com.alsaril.codegen.classfile.Fragment): List<StackMapFrame> =
    BytecodeSerializer.emit(fragment).second

fun handlers(block: CodeBuilder.() -> Unit): List<ExceptionHandler> =
    builder().apply(block).build().exceptionHandlers
