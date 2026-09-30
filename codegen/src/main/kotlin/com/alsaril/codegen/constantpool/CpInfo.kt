package com.alsaril.codegen.constantpool

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable

abstract class CpInfo(private val tag: Int) : Writable {
    final override fun ClassWriter.write() {
        u1(tag)
        writeInfo()
    }

    abstract fun ClassWriter.writeInfo()
}

data class ConstantUtf8Info(
    val value: String
) : CpInfo(tag = 1) {
    override fun ClassWriter.writeInfo() = utf8(value)
}

data class ConstantIntegerInfo(
    val value: Int
) : CpInfo(tag = 3) {
    override fun ClassWriter.writeInfo() = int(value)
}

data class ConstantFloatInfo(
    val value: Float
) : CpInfo(tag = 4) {
    override fun ClassWriter.writeInfo() = float(value)
}

data class ConstantLongInfo(
    val value: Long
) : CpInfo(tag = 5) {
    override fun ClassWriter.writeInfo() = long(value)
}

data class ConstantDoubleInfo(
    val value: Double
) : CpInfo(tag = 6) {
    override fun ClassWriter.writeInfo() = double(value)
}

data class ConstantClassInfo(
    val nameIndex: Int
) : CpInfo(tag = 7) {
    override fun ClassWriter.writeInfo() = u2(nameIndex)
}

data class ConstantStringInfo(
    val valueIndex: Int
) : CpInfo(tag = 8) {
    override fun ClassWriter.writeInfo() = u2(valueIndex)
}

data class ConstantFieldRefInfo(
    val classNameIndex: Int,
    val nameAndTypeIndex: Int
) : CpInfo(tag = 9) {
    override fun ClassWriter.writeInfo() {
        u2(classNameIndex)
        u2(nameAndTypeIndex)
    }
}

data class ConstantMethodRefInfo(
    val classNameIndex: Int,
    val nameAndTypeIndex: Int
) : CpInfo(tag = 10) {
    override fun ClassWriter.writeInfo() {
        u2(classNameIndex)
        u2(nameAndTypeIndex)
    }
}

data class ConstantInterfaceMethodRefInfo(
    val classNameIndex: Int,
    val nameAndTypeIndex: Int
) : CpInfo(tag = 11) {
    override fun ClassWriter.writeInfo() {
        u2(classNameIndex)
        u2(nameAndTypeIndex)
    }
}

data class ConstantNameAndTypeInfo(
    val nameIndex: Int,
    val descriptorIndex: Int
) : CpInfo(tag = 12) {
    override fun ClassWriter.writeInfo() {
        u2(nameIndex)
        u2(descriptorIndex)
    }
}

data class ConstantMethodHandleInfo(
    val referenceKind: ReferenceKind,
    val referenceIndex: Int
) : CpInfo(tag = 15) {
    override fun ClassWriter.writeInfo() {
        u1(referenceKind.tag)
        u2(referenceIndex)
    }

    enum class ReferenceKind(val tag: Int) {
        GET_FIELD(1),
        GET_STATIC(2),
        PUT_FIELD(3),
        PUT_STATIC(4),
        INVOKE_VIRTUAL(5),
        INVOKE_STATIC(6),
        INVOKE_SPECIAL(7),
        NEW_INVOKE_SPECIAL(8),
        INVOKE_INTERFACE(9),
    }
}

data class ConstantDynamicInfo(
    val bootstrapMethodIndex: Int,
    val nameAndTypeIndex: Int
) : CpInfo(tag = 17) {
    override fun ClassWriter.writeInfo() {
        u2(bootstrapMethodIndex)
        u2(nameAndTypeIndex)
    }
}

data class ConstantInvokeDynamicInfo(
    val bootstrapMethodIndex: Int,
    val nameAndTypeIndex: Int
) : CpInfo(tag = 18) {
    override fun ClassWriter.writeInfo() {
        u2(bootstrapMethodIndex)
        u2(nameAndTypeIndex)
    }
}
