package com.alsaril.codegen.classfile.code.instruction

import com.alsaril.codegen.ClassWriter

abstract class NoArgInstruction(val code: Int) : Instruction {
    override fun ClassWriter.write() = u1(code)
}

abstract class OneMixedArgInstruction(val code: Int, val arg: Int) : Instruction {
    override fun ClassWriter.write() {
        u1(code + arg)
    }
}

abstract class OneByteArgInstruction(val code: Int, val arg: Int) : Instruction {
    init {
        require(arg in 0..0xff) { "$arg does not fit a u1" }
    }

    override fun ClassWriter.write() {
        u1(code)
        u1(arg)
    }
}

abstract class OneSignedByteArgInstruction(val code: Int, val arg: Int) : Instruction {
    init {
        require(arg in Byte.MIN_VALUE..Byte.MAX_VALUE) { "$arg does not fit an s1" }
    }

    override fun ClassWriter.write() {
        u1(code)
        s1(arg)
    }
}

abstract class TwoBytesArgInstruction(val code: Int, val arg: Int) : Instruction {
    init {
        require(arg in 0..0xffff) { "$arg does not fit a u2" }
    }

    override fun ClassWriter.write() {
        u1(code)
        u2(arg)
    }
}

abstract class TwoSignedBytesArgInstruction(val code: Int, val arg: Int) : Instruction {
    init {
        require(arg in Short.MIN_VALUE..Short.MAX_VALUE) { "$arg does not fit an s2" }
    }

    override fun ClassWriter.write() {
        u1(code)
        s2(arg)
    }
}

abstract class WideTwoBytesArgInstruction(val code: Int, val arg: Int) : Instruction {
    init {
        require(arg in 0..0xffff) { "$arg does not fit a u2" }
    }

    override fun ClassWriter.write() {
        u1(0xc4)
        u1(code)
        u2(arg)
    }
}

abstract class JumpTemplateInstruction(val code: Int) : Instruction {
    override fun ClassWriter.write() {
        u1(code)
        s2(0)
    }
}
