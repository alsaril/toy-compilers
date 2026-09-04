package com.alsaril.codegen.constantpool

import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.*

class UpdatableConstantPool {
    private var index = 1
    private val entries = mutableListOf<CpInfo>()

    private val utf8Cache = mutableMapOf<String, Int>()
    private val stringCache = mutableMapOf<String, Int>()
    private val intCache = mutableMapOf<Int, Int>()
    private val longCache = mutableMapOf<Long, Int>()
    private val doubleCache = mutableMapOf<Double, Int>()
    private val classCache = mutableMapOf<String, Int>()
    private val nameAndTypeCache = mutableMapOf<Pair<String, String>, Int>()
    private val refCache = mutableMapOf<RefKey, Int>()

    private var built = false

    fun putUtf8(value: String) = utf8Cache.computeIfAbsent(value) {
        if (built) throw IllegalStateException("built")
        entries.add(ConstantUtf8Info(value))
        index++
    }

    fun putInt(value: Int) = intCache.computeIfAbsent(value) {
        if (built) throw IllegalStateException("built")
        entries.add(ConstantIntegerInfo(value))
        index++
    }

    fun putLong(value: Long) = longCache.computeIfAbsent(value) {
        if (built) throw IllegalStateException("built")
        entries.add(ConstantLongInfo(value))
        val pos = index
        index += 2
        pos
    }

    fun putDouble(value: Double) = doubleCache.computeIfAbsent(value) {
        if (built) throw IllegalStateException("built")
        entries.add(ConstantDoubleInfo(value))
        val pos = index
        index += 2
        pos
    }

    fun putClass(name: String) = classCache.computeIfAbsent(name) {
        if (built) throw IllegalStateException("built")
        val nameIndex = putUtf8(name)
        entries.add(ConstantClassInfo(nameIndex))
        index++
    }

    fun putString(value: String) = stringCache.computeIfAbsent(value) {
        if (built) throw IllegalStateException("built")
        val valueIndex = putUtf8(value)
        entries.add(ConstantStringInfo(valueIndex))
        index++
    }

    fun putConstantNameAndTypeInfo(name: String, descriptor: String) = nameAndTypeCache.computeIfAbsent(name to descriptor) {
        if (built) throw IllegalStateException("built")
        val nameIndex = putUtf8(name)
        val descriptorIndex = putUtf8(descriptor)
        entries.add(ConstantNameAndTypeInfo(nameIndex, descriptorIndex))
        index++
    }

    enum class RefType {
        FIELD, METHOD, INTERFACE_METHOD;
    }

    private data class RefKey(val classNameIndex: Int, val name: String, val descriptor: String, val refType: RefType)

    fun putRef(classNameIndex: Int, name: String, descriptor: String, refType: RefType) =
        refCache.computeIfAbsent(RefKey(classNameIndex, name, descriptor, refType)) {
            if (built) throw IllegalStateException("built")
            val nameAndTypeIndex = putConstantNameAndTypeInfo(name, descriptor)
            val info = when (refType) {
                FIELD -> ConstantFieldRefInfo(classNameIndex, nameAndTypeIndex)
                METHOD -> ConstantMethodRefInfo(classNameIndex, nameAndTypeIndex)
                INTERFACE_METHOD -> ConstantInterfaceMethodRefInfo(classNameIndex, nameAndTypeIndex)
            }
            entries.add(info)
            index++
        }

    fun build(): StaticConstantPool {
        built = true
        return StaticConstantPool(
            index,
            entries.toList()
        )
    }
}