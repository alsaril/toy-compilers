package com.alsaril.codegen

import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * The max_stack and max_locals a method declares. Both are derived rather than given, and
 * over-declaring either is legal, so neither is visible from loading or running the class.
 */
data class MethodLimits(val name: String, val maxStack: Int, val maxLocals: Int)

fun methodLimits(classBytes: ByteArray): List<MethodLimits> {
    val input = DataInputStream(ByteArrayInputStream(classBytes))
    input.skipNBytes(8) // magic, minor and major version

    val utf8 = mutableMapOf<Int, String>()
    val poolCount = input.readUnsignedShort()
    var index = 1
    while (index < poolCount) {
        when (input.readUnsignedByte()) {
            1 -> utf8[index] = input.readUTF()
            3, 4, 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4)
            5, 6 -> { input.skipNBytes(8); index++ } // eight byte constants take two slots
            7, 8, 16, 19, 20 -> input.skipNBytes(2)
            15 -> input.skipNBytes(3)
            else -> error("unknown constant pool tag")
        }
        index++
    }

    input.skipNBytes(6) // access flags, this class, super class
    input.skipNBytes(2L * input.readUnsignedShort()) // interfaces
    repeat(input.readUnsignedShort()) { // fields
        input.skipNBytes(6) // access flags, name, descriptor
        repeat(input.readUnsignedShort()) {
            input.skipNBytes(2) // attribute name
            input.skipNBytes(input.readInt().toLong())
        }
    }

    return List(input.readUnsignedShort()) {
        input.skipNBytes(2) // access flags
        val name = utf8[input.readUnsignedShort()]!!
        input.skipNBytes(2) // descriptor
        var limits = MethodLimits(name, -1, -1)
        repeat(input.readUnsignedShort()) {
            val attribute = utf8[input.readUnsignedShort()]
            val content = ByteArray(input.readInt()).also(input::readFully)
            if (attribute == "Code") {
                with(DataInputStream(ByteArrayInputStream(content))) {
                    limits = MethodLimits(name, readUnsignedShort(), readUnsignedShort())
                }
            }
        }
        limits
    }
}
