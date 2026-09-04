package com.alsaril.codegen.classfile

import com.alsaril.codegen.ClassWriter
import com.alsaril.codegen.Writable
import com.alsaril.codegen.constantpool.StaticConstantPool
import com.alsaril.codegen.write


data class ClassFile(
    val thisClassIndex: Int,
    val parentIndex: Int,
    val ifaceIndexes: List<Int>,
    val methods: List<MethodInfo>,
    val constantPool: StaticConstantPool,
) : Writable {
    private val ACC_PUBLIC = 0x0001
    private val ACC_FINAL = 0x0010

    override fun ClassWriter.write() {
        // magic
        byte(0xca); byte(0xfe); byte(0xba); byte(0xbe)

        // minor_version, major_version: 1.8
        short(0); short(52)

        // constant_pool_count, constant_pool
        write(constantPool)

        // access_flags
        short(ACC_PUBLIC or ACC_FINAL)

        // this_class
        short(thisClassIndex)

        // super_class: Object
        short(parentIndex)

        // interfaces_count: 1
        short(ifaceIndexes.size)

        // interfaces: single interface
        ifaceIndexes.forEach(::short)

        // fields_count, fields: 0
        short(0)

        // methods_count, methods
        short(methods.size)
        methods.forEach(::write)

        // attributes_count, attributes: 0
        short(0)
    }
}