package com.alsaril.codegen.code

import com.alsaril.codegen.constantpool.ClassPointer
import com.alsaril.codegen.constantpool.DataPointer

fun CodeBuilder.self() = ClassPointer(cp.putClass(thisClass))

fun CodeBuilder.parent() = ClassPointer(cp.putClass(parentClass))

fun CodeBuilder.clazz(name: String) = ClassPointer(cp.putClass(name))

fun CodeBuilder.int(value: Int) = DataPointer(cp.putInt(value))

fun CodeBuilder.float(value: Float) = DataPointer(cp.putFloat(value))

fun CodeBuilder.string(value: String) = DataPointer(cp.putString(value))
