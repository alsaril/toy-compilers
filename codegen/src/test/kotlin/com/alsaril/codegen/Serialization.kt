package com.alsaril.codegen

fun Writable.serialized(): ByteArray = toBytes { write(this@serialized) }

fun bytesOf(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
