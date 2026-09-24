package com.alsaril.scheme.runtime

interface Program {
    fun run(globalEnvironment: Context): Any
}