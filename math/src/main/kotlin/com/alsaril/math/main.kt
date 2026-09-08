package com.alsaril.math

import com.alsaril.math.MathCompiler.compile
import java.util.*
import kotlin.system.exitProcess

fun main(): Unit = exitProcess(run())

internal fun run(): Int {
    val scanner = Scanner(System.`in`)

    return try {
        val calculator = compile(scanner.nextLine())
        while (scanner.hasNextLine()) {
            println(calculator.eval(readVariables(scanner)))
        }
        0
    } catch (_: NoSuchElementException) {
        fail("no expression given")
    } catch (_: NullPointerException) {
        fail("the expression uses a variable the line does not give a value")
    } catch (e: IllegalArgumentException) {
        fail(e.message)
    }
}

private fun fail(message: String?): Int {
    System.err.println("Error: $message")
    return 1
}

private fun readVariables(scanner: Scanner): Map<String, Float> {
    val line = scanner.nextLine()
    return line.split(";")
        .asSequence()
        .filterNot { it.isBlank() }
        .associate {
            val parts = it.split("=")
            require(parts.size == 2) { "'$it' is not an assignment" }
            parts[0] to requireNotNull(parts[1].toFloatOrNull()) { "'${parts[1]}' is not a number" }
        }
}
