package com.alsaril.scheme.runtime

interface Program {
    fun run(environment: Environment): Any
}