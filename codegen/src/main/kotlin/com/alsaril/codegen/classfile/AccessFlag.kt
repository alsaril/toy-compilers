package com.alsaril.codegen.classfile

enum class AccessFlag(val value: Int) {
    PUBLIC(0x0001), PRIVATE(0x0002), STATIC(0x0008), FINAL(0x0010)
}