package com.alsaril.bf

import com.alsaril.bf.Compiler.compile
import java.io.File

fun main(args: Array<String>) {
    if (args.size != 1) {
        throw IllegalArgumentException("source file is expected")
    }
    val source = File(args[0]).readText()
    val program: ExtendedRunnable = compile(source)
    program.run(System.`in`, System.out, 30_000, Int.MAX_VALUE)
}