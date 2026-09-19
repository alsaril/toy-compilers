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
        u1(0xca); u1(0xfe); u1(0xba); u1(0xbe)

        // minor_version, major_version: 1.8
        u2(0); u2(52)

        // constant_pool_count, constant_pool
        write(constantPool)

        // access_flags
        u2(ACC_PUBLIC or ACC_FINAL)

        // this_class
        u2(thisClassIndex)

        // super_class: Object
        u2(parentIndex)

        // interfaces_count: 1
        u2(ifaceIndexes.size)

        // interfaces: single interface
        ifaceIndexes.forEach(::u2)

        // fields_count, fields: 0
        u2(0)

        // methods_count, methods
        u2(methods.size)
        methods.forEach(::write)

        // attributes_count, attributes: 0
        u2(0)
    }
}