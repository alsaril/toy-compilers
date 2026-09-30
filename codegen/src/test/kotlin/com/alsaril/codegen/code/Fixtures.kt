package com.alsaril.codegen.code

import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.classfile.attributes.FullFrame
import com.alsaril.codegen.classfile.attributes.StackMapFrame
import com.alsaril.codegen.constantpool.UpdatableConstantPool

const val THIS_CLASS = "This"
const val PARENT_CLASS = "Parent"

fun builder(cp: UpdatableConstantPool = UpdatableConstantPool()) = CodeBuilder(cp, BootstrapMethods(), THIS_CLASS, PARENT_CLASS)

fun bytecode(block: CodeBuilder.() -> Unit): ByteArray = builder().apply(block).build().bytecode()

fun Fragment.bytecode(): ByteArray = BytecodeSerializer.emit(this, emptyList()).first

/** What the analyzer derives for a body of [THIS_CLASS]: max_stack, max_locals and the frames by instruction index. */
fun analysis(
    cp: UpdatableConstantPool = UpdatableConstantPool(),
    descriptor: String = "()V",
    static: Boolean = true,
    constructor: Boolean = false,
    hierarchy: ClassHierarchy = LenientHierarchy,
    block: CodeBuilder.() -> Unit,
): Triple<Int, Int, List<FullFrame>> =
    Analyzer(cp, THIS_CLASS, hierarchy).analyze(builder(cp).apply(block).build(), descriptor, constructor, static)

/** The frames as the stack map table holds them: laid out in bytes, each measured from the one before. */
fun frames(
    cp: UpdatableConstantPool = UpdatableConstantPool(),
    descriptor: String = "()V",
    block: CodeBuilder.() -> Unit,
): List<StackMapFrame> {
    val fragment = builder(cp).apply(block).build()
    val derived = Analyzer(cp, THIS_CLASS, LenientHierarchy).analyze(fragment, descriptor, false, true).third
    return BytecodeSerializer.emit(fragment, derived).second
}

fun handlersOf(fragment: Fragment): List<ExceptionHandler> =
    BytecodeSerializer.emit(fragment, emptyList()).third

fun handlers(block: CodeBuilder.() -> Unit): List<ExceptionHandler> =
    builder().apply(block).build().exceptionHandlers
