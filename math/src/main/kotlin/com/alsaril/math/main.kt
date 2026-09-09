package com.alsaril.math

import com.alsaril.math.MathCompiler.compile
import java.util.*
import kotlin.system.exitProcess

fun main(): Unit = exitProcess(run())

internal fun run(): Int {
    val scanner = Scanner(System.`in`)

    val expr = try {
        print("expr> ")
        scanner.nextLine()
    } catch (_: NoSuchElementException) {
        return fail("no expression given")
    }

    val program = try {
        compile(expr)
    } catch (e: IllegalArgumentException) {
        return fail("could not compile expression: ${e.message}")
    }

    while (true) {
        try {
            print("vars> ")
            if (!scanner.hasNextLine()) return 0
            println(program.eval(readVariables(scanner)))
        } catch (e: NoSuchElementException) {
            println("no variable with name ${e.message} is found")
        } catch (e: IllegalArgumentException) {
            println("error: ${e.message}")
        }
    }
}

private fun fail(message: String?): Int {
    System.err.println(message)
    return 1
}

private fun readVariables(scanner: Scanner): Map<String, Float> {
    val line = scanner.nextLine()
    return line.split(";")
        .asSequence()
        .filterNot { it.isBlank() }
        .associate { assigment ->
            val parts = assigment.split("=")
            require(parts.size == 2) { "'$assigment' is not an assignment" }
            parts[0].trim() to parts[1].trim().let { requireNotNull(it.toFloatOrNull()) { "'$it' is not a number" } }
        }
}
