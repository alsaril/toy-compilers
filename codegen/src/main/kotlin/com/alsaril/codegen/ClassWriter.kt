package com.alsaril.codegen

import java.io.ByteArrayOutputStream
import java.io.DataOutput
import java.io.DataOutputStream

interface ClassWriter {
    fun s1(value: Int)
    fun u1(value: Int)
    fun s2(value: Int)
    fun u2(value: Int)
    fun int(value: Int)
    fun float(value: Float)
    fun long(value: Long)
    fun double(value: Double)
    fun bytes(value: ByteArray)
    fun utf8(value: String)
}

@Suppress("NOTHING_TO_INLINE")
inline fun ClassWriter.write(w: Writable) = w.apply { this@write.write() }

fun toBytes(serializer: ClassWriter.() -> Unit): ByteArray {
    val bos = ByteArrayOutputStream()
    DosWriter(DataOutputStream(bos)).apply(serializer)
    return bos.toByteArray()
}

// every u1 and u2 in a class file passes through here, so the width checks live here
// rather than at each of the several dozen call sites
class DosWriter(private val dos: DataOutput) : ClassWriter {
    override fun s1(value: Int) {
        require(value in Byte.MIN_VALUE..Byte.MAX_VALUE) { "$value does not fit an s1" }
        dos.writeByte(value)
    }

    override fun u1(value: Int) {
        require(value in 0..0xff) { "$value does not fit a u1" }
        dos.writeByte(value)
    }

    override fun s2(value: Int) {
        require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "$value does not fit an s2" }
        dos.writeShort(value)
    }

    override fun u2(value: Int) {
        require(value in 0..0xffff) { "$value does not fit a u2" }
        dos.writeShort(value)
    }

    override fun int(value: Int) = dos.writeInt(value)
    override fun float(value: Float) = dos.writeFloat(value)
    override fun long(value: Long) = dos.writeLong(value)
    override fun double(value: Double) = dos.writeDouble(value)
    override fun bytes(value: ByteArray) = dos.write(value)
    override fun utf8(value: String) = dos.writeUTF(value)
}