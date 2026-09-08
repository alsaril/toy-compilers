package com.alsaril.math

import com.alsaril.math.MathCompiler.compile
import java.util.*

fun main() {
    val scanner = Scanner(System.`in`)
    val input = scanner.nextLine()
    val calculator = compile(input)
    while (true) {
        val variables = readVariables(scanner)
        if (variables.isEmpty()) break
        val result = calculator.eval(variables)
        println(result)
    }
}

private fun readVariables(scanner: Scanner): Map<String, Float> {
    val line = scanner.nextLine()
    return line.split(";")
        .asSequence()
        .filterNot { it.isBlank() }
        .associate {
            val parts = it.split("=")
            if (parts.size != 2) {
                throw IllegalArgumentException()
            }
            parts[0] to parts[1].toFloat()
        }
}