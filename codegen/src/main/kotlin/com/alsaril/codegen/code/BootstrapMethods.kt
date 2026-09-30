package com.alsaril.codegen.code

import com.alsaril.codegen.classfile.attributes.BootstrapMethod

class BootstrapMethods {
    private val bootstrapMethods = mutableMapOf<BootstrapMethod, Int>()

    fun isEmpty() = bootstrapMethods.isEmpty()

    fun add(bootstrapMethod: BootstrapMethod) =
        bootstrapMethods.computeIfAbsent(bootstrapMethod) { bootstrapMethods.size }

    fun methods() = bootstrapMethods.keys.toList()
}