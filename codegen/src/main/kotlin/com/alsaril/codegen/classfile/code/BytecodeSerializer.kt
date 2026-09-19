package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.DosWriter
import com.alsaril.codegen.classfile.Fragment
import com.alsaril.codegen.classfile.attributes.CodeAttribute
import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.classfile.attributes.StackMapFrame
import com.alsaril.codegen.classfile.attributes.StackMapTableAttribute
import com.alsaril.codegen.write
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.*
import kotlin.math.max

object BytecodeSerializer {
    fun serialize(fragment: Fragment, headerLocals: Int, cpEntry: (String) -> Int): CodeAttribute {
        val (maxStack, maxLocals) = Pair(0, 0)// analyze(fragment)
        val (bytecode, frames, exceptionHandlers) = emit(fragment)

        return CodeAttribute(
            cpEntry("Code"),
            maxStack,
            max(headerLocals, maxLocals),
            bytecode,
            exceptionHandlers,
            listOf(StackMapTableAttribute(cpEntry("StackMapTable"), frames)),
        )
    }

    internal fun emit(fragment: Fragment): Triple<ByteArray, List<StackMapFrame>, List<ExceptionHandler>> {
        val output = ByteArrayOutputStream()
        val writer = DosWriter(DataOutputStream(output))
        val i2loc = IdentityHashMap<Instruction, Int>()

        fragment.instructions.forEach {
            i2loc[it] = output.size()
            writer.write(it)
        }

        val code = output.toByteArray()

        fragment.jumps.forEach { (from, to) ->
            when (from) {
                IfEq, IfNe, IfGe, IfGt, IfICmpLt, IfICmpGe, Goto, IfNull, IfNotNull -> {
                    code.s2At(i2loc[to]!!, i2loc[from]!! + 1)
                }

                else -> throw IllegalArgumentException()
            }
        }

        // frames

        // handlers

        return Triple(code, emptyList(), emptyList())
    }

    internal fun ByteArray.s2At(value: Int, pos: Int) {
        require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "$value does not fit an s2" }
        this[pos] = (value shr 8).toByte()
        this[pos + 1] = value.toByte()
    }
}