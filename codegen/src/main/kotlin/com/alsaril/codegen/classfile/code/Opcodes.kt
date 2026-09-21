package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.classfile.code.instruction.*

fun CodeBuilder.nop() = add(Nop)

// const
fun CodeBuilder.aconst_null() = add(AConstNull)

fun CodeBuilder.iconst(value: Int) =
    when (value) {
        in -1..5 -> IConst(value)
        in Byte.MIN_VALUE..Byte.MAX_VALUE -> BIPush(value)
        in Short.MIN_VALUE..Short.MAX_VALUE -> SIPush(value)
        else -> throw IllegalArgumentException("The value is too big for iconst/bipush/sipush, ldc should be used")
    }.let(::add)

fun CodeBuilder.fconst(value: Int) =
    if (value in 0..2) {
        add(FConst(value))
    } else {
        throw IllegalArgumentException("The value is out of range for fconst, ldc should be used")
    }

fun CodeBuilder.ldc(pointer: DataPointer) = add(Ldc(pointer.index))

// load
fun CodeBuilder.iload(index: Int) = add(ILoad(index))

fun CodeBuilder.fload(index: Int) = add(FLoad(index))

fun CodeBuilder.aload(index: Int) = add(ALoad(index))

fun CodeBuilder.iaload() = add(IALoad)

fun CodeBuilder.baload() = add(BALoad)

// store
fun CodeBuilder.istore(index: Int) = add(IStore(index))

fun CodeBuilder.fstore(index: Int) = add(FStore(index))

fun CodeBuilder.astore(index: Int) = add(AStore(index))

fun CodeBuilder.iastore() = add(IAStore)

fun CodeBuilder.bastore() = add(BAStore)

// math
fun CodeBuilder.iadd() = add(IAdd)

fun CodeBuilder.isub() = add(ISub)

fun CodeBuilder.fadd() = add(FAdd)

fun CodeBuilder.fsub() = add(FSub)

fun CodeBuilder.fmul() = add(FMul)

fun CodeBuilder.fdiv() = add(FDiv)

fun CodeBuilder.fneg() = add(FNeg)

fun CodeBuilder.iinc(index: Int, const: Int) = add(IInc(index, const))

// stack
fun CodeBuilder.dup() = add(Dup)

fun CodeBuilder.dup2() = add(Dup2)

fun CodeBuilder.dup_x2() = add(DupX2)

// return
fun CodeBuilder.ireturn() = add(IReturn)

fun CodeBuilder.freturn() = add(FReturn)

fun CodeBuilder.`return`() = add(Return)

fun CodeBuilder.athrow() = add(AThrow)

