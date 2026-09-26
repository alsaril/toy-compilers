package com.alsaril.codegen.code

import com.alsaril.codegen.constantpool.ClassPointer
import com.alsaril.codegen.constantpool.DataPointer
import com.alsaril.codegen.instruction.PrimitiveType.FLOAT
import com.alsaril.codegen.instruction.PrimitiveType.INTEGER
import com.alsaril.codegen.instruction.ReferenceType

fun CodeBuilder.self() = clazz(thisClass)

fun CodeBuilder.parent() = clazz(parentClass)

fun CodeBuilder.clazz(name: String) = ClassPointer(cp.putClass(name), name)

fun CodeBuilder.int(value: Int) = DataPointer(cp.putInt(value), INTEGER)

fun CodeBuilder.float(value: Float) = DataPointer(cp.putFloat(value), FLOAT)

fun CodeBuilder.string(value: String) = DataPointer(cp.putString(value), ReferenceType("java/lang/String"))
