package com.alsaril.codegen.instruction

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

abstract class TwoBytesArgInstruction(val code: Int, val arg: Int) : Instruction {
    init {
        require(arg in 0..0xffff) { "$arg does not fit a u2" }
    }

    override fun ClassWriter.write() {
        u1(code)
        u2(arg)
    }
}

abstract class LocalSlotInstruction(
    private val compact: Int,
    private val code: Int,
) : TouchesLocal {
    override fun ClassWriter.write() {
        require(index in 0..0xffff) { "$index does not fit a u2" }
        when {
            index < 4 -> u1(compact + index)
            index < 0x100 -> { u1(code); u1(index) }
            else -> { u1(0xc4); u1(code); u2(index) }
        }
    }
}

abstract class JumpInstruction(val code: Int) : Instruction {
    override fun ClassWriter.write() {
        u1(code)
        s2(0)
    }
}
