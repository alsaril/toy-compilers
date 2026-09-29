package com.alsaril.codegen

typealias ClassDef = Pair<String, ByteArray>

data class ClassGraph(val root: ClassDef, val deps: List<ClassDef> = emptyList())