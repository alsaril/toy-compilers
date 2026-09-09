package com.alsaril.math

import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * Reads the max_stack of every method in a class file, so a test can check the depth the
 * generator asked for rather than the depth it meant to ask for. Declaring more than a
 * body needs is legal, and therefore invisible from running the class.
 */
fun maxStacks(classBytes: ByteArray): List<Int> {
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
    require(input.readUnsignedShort() == 0) { "the generator does not emit fields" }

    return List(input.readUnsignedShort()) {
        input.skipNBytes(6) // access flags, name, descriptor
        var maxStack = -1
        repeat(input.readUnsignedShort()) {
            val name = utf8[input.readUnsignedShort()]
            val content = ByteArray(input.readInt()).also(input::readFully)
            if (name == "Code") {
                maxStack = DataInputStream(ByteArrayInputStream(content)).readUnsignedShort()
            }
        }
        maxStack
    }
}
