package com.alsaril.codegen.classfile

import bf.compiler.attributes.AppendFrame
import bf.compiler.attributes.ObjectVariableInfo
import bf.compiler.attributes.SimpleVerificationTypeInfo.IntegerVariableInfo
import bf.compiler.attributes.StackMapFrame
import bf.compiler.attributes.sameFrame
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.FIELD
import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.METHOD

class CodeBuilder(
    private val cp: UpdatableConstantPool,
    private val thisClass: String,
    private val parentClass: String,
) {
    private val bytecode = mutableListOf<Byte>()
    private val stackMapFrames = mutableListOf<StackMapFrame>()
    private var base = 0

    fun build(): Pair<ByteArray, List<StackMapFrame>> = bytecode.toByteArray() to stackMapFrames

    fun method(classPointer: ClassPointer, name: String, descriptor: String): MethodDescriptor {
        val classNameIndex = cp.putClass(classPointer.name)
        val ref = cp.putRef(classNameIndex, name, descriptor, METHOD)
        return MethodDescriptor(ref)
    }

    fun field(classPointer: ClassPointer, name: String, descriptor: String): FieldDescriptor {
        val classNameIndex = cp.putClass(classPointer.name)
        val ref = cp.putRef(classNameIndex, name, descriptor, FIELD)
        return FieldDescriptor(ref)
    }

    fun self() = ClassPointer(thisClass)
    fun parent() = ClassPointer(parentClass)
    fun clazz(name: String) = ClassPointer(name)

    fun frameSame() {
        val l = loc()
        stackMapFrames.add(sameFrame(l - base))
        base = l + 1
    }

    fun frameAppend(vararg varInfos: VarInfo) {
        val l = loc()
        stackMapFrames.add(
            AppendFrame(
                l - base,
                varInfos.map {
                    when (it) {
                        IntInfo -> IntegerVariableInfo
                        is ObjInfo -> ObjectVariableInfo(cp.putClass(it.descriptor))
                    }
                }
            )
        )
        base = l + 1
    }

    sealed interface VarInfo
    data object IntInfo : VarInfo
    data class ObjInfo(val descriptor: String) : VarInfo

    private fun b1(value: Int) {
        bytecode.add(value.toByte())
    }

    private fun b2(value: Int) {
        b1(value shr 8)
        b1(value and 0xff)
    }

    fun loc() = bytecode.size

    fun b1At(code: Int, pos: Int) {
        bytecode[pos] = code.toByte()
    }

    fun b2At(value: Int, pos: Int) {
        b1At(value shr 8, pos)
        b1At(value and 0xff, pos + 1)
    }

    fun nop() {
        b1(0x00)
    }

    // const
    fun aconst_null() {
        b1(0x01)
    }

    fun iconst(value: Int) {
        if (value >= -1 && value <= 5) {
            b1(0x03 + value) // iconst
        } else if (value in Byte.MIN_VALUE..Byte.MAX_VALUE) {
            b1(0x10) // bipush
            b1(value)
        } else if (value in Short.MIN_VALUE..Short.MAX_VALUE) {
            b1(0x11) // sipush
            b2(value)
        } else {
            throw IllegalArgumentException("The value is too big for iconst/bipush/sipush, ldc should be used")
        }
    }

    // load
    fun iload(index: Int) {
        instructionFamily(index, 0x1a, 0x15)
    }

    fun aload(index: Int) {
        instructionFamily(index, 0x2a, 0x19)
    }

    fun baload() {
        b1(0x33)
    }

    // store
    fun istore(index: Int) {
        instructionFamily(index, 0x3b, 0x36)
    }

    fun astore(index: Int) {
        instructionFamily(index, 0x4b, 0x3a)
    }

    fun bastore() {
        b1(0x54)
    }

    // math
    fun iadd() {
        b1(0x60)
    }

    fun isub() {
        b1(0x64)
    }

    fun iinc(index: Int, const: Int) {
        if (index < 0x100 && const in Byte.MIN_VALUE..Byte.MAX_VALUE) {
            b1(0x84)
            b1(index)
            b1(const)
        } else { // wide
            require(const in Short.MIN_VALUE..Short.MAX_VALUE)
            b1(0xc4)
            b1(0x84)
            b2(index)
            b2(const)
        }
    }

    // stack
    fun dup() {
        b1(0x59)
    }

    fun dup2() {
        b1(0x5c)
    }

    // get
    fun getstatic(fieldDescriptor: FieldDescriptor) {
        b1(0xb2)
        b2(fieldDescriptor.index)
    }

    // return
    fun `return`() {
        b1(0xb1)
    }

    fun athrow() {
        b1(0xbf)
    }

    // invoke
    fun invokevirtual(methodDescriptor: MethodDescriptor) {
        b1(0xb6)
        b2(methodDescriptor.index)
    }

    fun invokespecial(methodDescriptor: MethodDescriptor) {
        b1(0xb7)
        b2(methodDescriptor.index)
    }

    fun invokestatic(methodDescriptor: MethodDescriptor) {
        b1(0xb8)
        b2(methodDescriptor.index)
    }

    fun invokeinterface(methodDescriptor: MethodDescriptor, count: Int) {
        b1(0xb9)
        b2(methodDescriptor.index)
        b1(count)
        b1(0)
    }

    // new
    fun new(classIndex: Int) {
        b1(0xbb)
        b2(classIndex)
    }

    enum class ArrayType(val index: Int) {
        BYTE(8);
    }

    fun newarray(type: ArrayType) {
        b1(0xbc)
        b1(type.index)
    }

    fun construct(classPointer: ClassPointer, name: String, descriptor: String) {
        val idx = cp.putClass(classPointer.name)
        new(idx)
        dup()
        invokespecial(method(classPointer, name, descriptor))
    }

    // jump
    fun goto(dest: Int? = null) = jumpTemplate(0xa7, dest)

    fun ifeq(dest: Int? = null) = jumpTemplate(0x99, dest)

    fun ifne(dest: Int? = null) = jumpTemplate(0x9a, dest)

    fun if_icmplt(dest: Int? = null) = jumpTemplate(0xa1, dest)

    fun if_icmpge(dest: Int? = null) = jumpTemplate(0xa2, dest)

    // helpers
    private fun instructionFamily(index: Int, short: Int, long: Int, limit: Int = 4) {
        if (index < limit) {
            b1(short + index)
        } else if (index < 0x100) {
            b1(long)
            b1(index)
        } else {
            throw NotImplementedError()
        }
    }

    private fun jumpTemplate(opcode: Int, dest: Int?): (Int) -> Unit {
        val start = loc()
        b1(opcode)
        val pos = loc()
        if (dest != null) {
            b2(dest - start)
            return {}
        } else {
            b2(0) // placeholder
            return { dest -> b2At(dest - start, pos) }
        }
    }

    data class ClassPointer(val name: String)

    data class MethodDescriptor(val index: Int)

    data class FieldDescriptor(val index: Int)
}