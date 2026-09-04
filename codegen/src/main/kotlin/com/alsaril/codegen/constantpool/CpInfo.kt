package com.alsaril.codegen.constantpool

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable

abstract class CpInfo(private val tag: Int) : Writable {
    final override fun ClassWriter.write() {
        byte(tag)
        writeInfo()
    }

    abstract fun ClassWriter.writeInfo()
}

class ConstantUtf8Info(
    private val value: String
) : CpInfo(tag = 1) {
    override fun ClassWriter.writeInfo() = utf8(value)
}

class ConstantIntegerInfo(
    val value: Int
) : CpInfo(tag = 3) {
    override fun ClassWriter.writeInfo() = int(value)
}

class ConstantLongInfo(
    val value: Long
) : CpInfo(tag = 5) {
    override fun ClassWriter.writeInfo() = long(value)
}

class ConstantDoubleInfo(
    private val value: Double
) : CpInfo(tag = 6) {
    override fun ClassWriter.writeInfo() = double(value)
}

class ConstantClassInfo(
    private val nameIndex: Int
) : CpInfo(tag = 7) {
    override fun ClassWriter.writeInfo() = short(nameIndex)
}

class ConstantStringInfo(
    private val valueIndex: Int
) : CpInfo(tag = 8) {
    override fun ClassWriter.writeInfo() = short(valueIndex)
}

class ConstantFieldRefInfo(
    private val classNameIndex: Int,
    private val nameAndTypeIndex: Int
) : CpInfo(tag = 9) {
    override fun ClassWriter.writeInfo() {
        short(classNameIndex)
        short(nameAndTypeIndex)
    }
}

class ConstantMethodRefInfo(
    private val classNameIndex: Int,
    private val nameAndTypeIndex: Int
) : CpInfo(tag = 10) {
    override fun ClassWriter.writeInfo() {
        short(classNameIndex)
        short(nameAndTypeIndex)
    }
}

class ConstantInterfaceMethodRefInfo(
    private val classNameIndex: Int,
    private val nameAndTypeIndex: Int
) : CpInfo(tag = 11) {
    override fun ClassWriter.writeInfo() {
        short(classNameIndex)
        short(nameAndTypeIndex)
    }
}

class ConstantNameAndTypeInfo(
    private val nameIndex: Int,
    private val descriptorIndex: Int
) : CpInfo(tag = 12) {
    override fun ClassWriter.writeInfo() {
        short(nameIndex)
        short(descriptorIndex)
    }
}
