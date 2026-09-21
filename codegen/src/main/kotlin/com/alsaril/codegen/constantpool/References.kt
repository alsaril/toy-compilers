package com.alsaril.codegen.constantpool

data class ClassPointer(val index: Int)

data class MethodDescriptor(val index: Int, val argSlots: Int, val returnSlots: Int)

data class FieldDescriptor(val index: Int, val slots: Int)

data class DataPointer(val index: Int)
