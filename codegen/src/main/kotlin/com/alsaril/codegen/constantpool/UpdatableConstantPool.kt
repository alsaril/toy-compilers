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

    fun putUtf8(value: String) = utf8Cache.computeIfAbsent(value) {
        entries.add(ConstantUtf8Info(value))
        index++
    }

    fun putInt(value: Int) = intCache.computeIfAbsent(value) {
        entries.add(ConstantIntegerInfo(value))
        index++
    }

    fun putLong(value: Long) = longCache.computeIfAbsent(value) {
        entries.add(ConstantLongInfo(value))
        val pos = index
        index += 2
        pos
    }

    fun putDouble(value: Double) = doubleCache.computeIfAbsent(value) {
        entries.add(ConstantDoubleInfo(value))
        val pos = index
        index += 2
        pos
    }

    fun putClass(name: String) = classCache.computeIfAbsent(name) {
        val nameIndex = putUtf8(name)
        entries.add(ConstantClassInfo(nameIndex))
        index++
    }

    fun putString(value: String) = stringCache.computeIfAbsent(value) {
        val valueIndex = putUtf8(value)
        entries.add(ConstantStringInfo(valueIndex))
        index++
    }

    fun putConstantNameAndTypeInfo(name: String, type: String) = nameAndTypeCache.computeIfAbsent(name to type) {
        val nameIndex = putUtf8(name)
        val typeIndex = putUtf8(type)
        entries.add(ConstantNameAndTypeInfo(nameIndex, typeIndex))
        index++
    }

    enum class RefType {
        FIELD, METHOD, INTERFACE_METHOD;
    }

    private data class RefKey(val classNameIndex: Int, val name: String, val type: String, val refType: RefType)

    fun putRef(classNameIndex: Int, name: String, type: String, refType: RefType) =
        refCache.computeIfAbsent(RefKey(classNameIndex, name, type, refType)) {
            val nameAndTypeIndex = putConstantNameAndTypeInfo(name, type)
            val info = when (refType) {
                FIELD -> ConstantFieldRefInfo(classNameIndex, nameAndTypeIndex)
                METHOD -> ConstantMethodRefInfo(classNameIndex, nameAndTypeIndex)
                INTERFACE_METHOD -> ConstantInterfaceMethodRefInfo(classNameIndex, nameAndTypeIndex)
            }
            entries.add(info)
            index++
        }

    fun build() = StaticConstantPool(
        index,
        entries.toList()
    )
}