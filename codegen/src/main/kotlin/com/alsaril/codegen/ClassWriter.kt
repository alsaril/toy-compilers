package com.alsaril.codegen

import java.io.ByteArrayOutputStream
import java.io.DataOutput
import java.io.DataOutputStream

interface ClassWriter {
    fun byte(value: Int)
    fun short(value: Int)
    fun int(value: Int)
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

class DosWriter(private val dos: DataOutput) : ClassWriter {
    override fun byte(value: Int) = dos.writeByte(value)
    override fun short(value: Int) = dos.writeShort(value)
    override fun int(value: Int) = dos.writeInt(value)
    override fun long(value: Long) = dos.writeLong(value)
    override fun double(value: Double) = dos.writeDouble(value)
    override fun bytes(value: ByteArray) = dos.write(value)
    override fun utf8(value: String) = dos.writeUTF(value)
}