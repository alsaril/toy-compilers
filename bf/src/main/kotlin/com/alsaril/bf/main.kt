package com.alsaril.bf

import com.alsaril.bf.Compiler.compile
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo

internal class BfCommand : CliktCommand(name = "bf") {

    init {
        context { helpFormatter = { MordantHelpFormatter(it, showDefaultValues = true) } }
    }

    override fun help(context: Context) = "Compile a Brainfuck program to a JVM class and run it"

    private val source by argument()
        .help("the program to run")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    private val memsize by option()
        .help("tape length in cells")
        .int()
        .restrictTo(min = 1)
        .default(30_000)

    private val cycles by option()
        .help("loop iterations allowed before the program is stopped")
        .int()
        .restrictTo(min = 0)
        .default(Int.MAX_VALUE)

    override fun run() {
        val program = try {
            compile(source.readText())
        } catch (e: IllegalArgumentException) {
            fail("${source.name}: ${e.message}")
        }

        try {
            program.run(System.`in`, System.out, memsize, cycles)
        } catch (e: IllegalStateException) {
            System.out.flush()
            fail(e.message)
        }
    }

    private fun fail(message: String?): Nothing = throw CliktError("Error: $message")
}

fun main(args: Array<String>) = BfCommand().main(args)
