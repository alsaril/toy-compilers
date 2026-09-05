package com.alsaril.bf

import com.alsaril.bf.Compiler.compile
import java.io.File

fun main(args: Array<String>) {
    if (args.size != 1) return
    val source = File(args[0]).readText()
    val program: ExtendedRunnable = compile(source)
    program.run(System.`in`, System.out, 10000, 10000)
}