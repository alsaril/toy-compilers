package com.alsaril.codegen.constantpool

import com.alsaril.codegen.instruction.VerificationType

data class ClassPointer(val index: Int, val name: String)

data class MethodDescriptor(val index: Int, val args: List<VerificationType>, val returnType: VerificationType)

data class FieldDescriptor(val index: Int, val ownerType: VerificationType, val type: VerificationType)

data class DataPointer(val index: Int, val type: VerificationType)
