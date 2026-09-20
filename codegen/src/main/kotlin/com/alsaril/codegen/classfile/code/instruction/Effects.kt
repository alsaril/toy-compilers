package com.alsaril.codegen.classfile.code.instruction

internal interface PushesOne : Instruction {
    override fun stackEffects() = 0 to 1
}

internal interface PopsOne : Instruction {
    override fun stackEffects() = 1 to 0
}

internal interface PopsTwo : Instruction {
    override fun stackEffects() = 2 to 0
}

internal interface PopsThree : Instruction {
    override fun stackEffects() = 3 to 0
}

internal interface PopsOnePushesOne : Instruction {
    override fun stackEffects() = 1 to 1
}

internal interface PopsTwoPushesOne : Instruction {
    override fun stackEffects() = 2 to 1
}

internal interface Invocation : Instruction {
    val argSlots: Int
    val returnSlots: Int

    override fun stackEffects() = argSlots to returnSlots
}

internal interface TouchesLocal : Instruction {
    val index: Int

    override fun locals() = index
}
