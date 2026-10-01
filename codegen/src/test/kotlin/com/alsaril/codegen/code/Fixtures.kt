package com.alsaril.codegen.code

import com.alsaril.codegen.assembly.BytecodeSerializer
import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import com.alsaril.codegen.instruction.Fragment

const val THIS_CLASS = "This"
const val PARENT_CLASS = "Parent"

fun builder(cp: UpdatableConstantPool = UpdatableConstantPool()) = CodeBuilder(cp, BootstrapMethods(), THIS_CLASS, PARENT_CLASS)

fun bytecode(block: CodeBuilder.() -> Unit): ByteArray = builder().apply(block).build().bytecode()

fun Fragment.bytecode(): ByteArray = BytecodeSerializer.emit(this, emptyList()).first

fun handlersOf(fragment: Fragment): List<ExceptionHandler> =
    BytecodeSerializer.emit(fragment, emptyList()).third

fun handlers(block: CodeBuilder.() -> Unit): List<ExceptionHandler> =
    builder().apply(block).build().exceptionHandlers
