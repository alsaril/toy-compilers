package com.alsaril.codegen.assembly

interface ClassHierarchy {
    fun isAssignable(from: String, to: String): Boolean
    fun commonSuperclass(a: String, b: String): String
}

object LenientHierarchy : ClassHierarchy {
    override fun isAssignable(from: String, to: String) = true
    override fun commonSuperclass(a: String, b: String) = if (a == b) a else "java/lang/Object"
}
