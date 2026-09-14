package com.alsaril.math

import java.io.ByteArrayInputStream
import java.io.DataInputStream

fun methodNames(classBytes: ByteArray): List<String> = methods(classBytes) { name, _ -> name }

fun maxStacks(classBytes: ByteArray): List<Int> = methods(classBytes) { _, code ->
    code?.let { DataInputStream(ByteArrayInputStream(it)).readUnsignedShort() } ?: -1
}

fun maxLocals(classBytes: ByteArray): List<Int> = methods(classBytes) { _, code ->
    code?.let {
        with(DataInputStream(ByteArrayInputStream(it))) { readUnsignedShort(); readUnsignedShort() }
    } ?: -1
}

private fun <T> methods(classBytes: ByteArray, read: (name: String, code: ByteArray?) -> T): List<T> {
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
        input.skipNBytes(2) // access flags
        val name = utf8[input.readUnsignedShort()]!!
        input.skipNBytes(2) // descriptor
        var code: ByteArray? = null
        repeat(input.readUnsignedShort()) {
            val attribute = utf8[input.readUnsignedShort()]
            val content = ByteArray(input.readInt()).also(input::readFully)
            if (attribute == "Code") code = content
        }
        read(name, code)
    }
}
