package com.alsaril.math

import com.alsaril.math.MathCompiler.compile
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import java.util.*

internal class MathCommand : CliktCommand(name = "math") {

    init {
        context { helpFormatter = { MordantHelpFormatter(it, showDefaultValues = true) } }
    }

    override fun help(context: Context) = "Compile a math expression to a JVM class and evaluate it"

    private val interactive by option()
        .help("prompt for the expression and for each set of variables, keeping going after anything that fails")
        .flag()

    private val expr by option()
        .help("the expression to evaluate, required unless --interactive is given")

    private val vars by option()
        .help("assignments to evaluate the expression against, separated by ';'. Repeat for a line of output each")
        .multiple()

    override fun run() = if (interactive) evaluateInteractively() else evaluateGivenArguments()

    private fun evaluateGivenArguments() {
        val source = expr ?: throw UsageError("--expr is required unless --interactive is given")

        val program = try {
            compile(source)
        } catch (e: IllegalArgumentException) {
            fail("could not compile expression: ${e.message}")
        }

        var anyFailed = false
        vars.forEach { line ->
            try {
                echo(program.eval(readVariables(line)))
            } catch (e: NoSuchElementException) {
                anyFailed = true
                echo(FAILED)
                echo("no variable with name ${e.message} is found", err = true)
            } catch (e: IllegalArgumentException) {
                anyFailed = true
                echo(FAILED)
                echo(e.message, err = true)
            }
        }

        if (anyFailed) throw ProgramResult(1)
    }

    private fun evaluateInteractively() {
        if (expr != null || vars.isNotEmpty()) {
            throw UsageError("--expr and --vars cannot be used with --interactive")
        }

        val scanner = Scanner(System.`in`)
        val program = compileWhatIsTyped(scanner) ?: fail("no expression given")

        while (true) {
            echo("vars> ", trailingNewline = false)
            if (!scanner.hasNextLine()) return

            try {
                echo(program.eval(readVariables(scanner.nextLine())))
            } catch (e: NoSuchElementException) {
                echo("no variable with name ${e.message} is found")
            } catch (e: IllegalArgumentException) {
                echo("error: ${e.message}")
            }
        }
    }

    private fun compileWhatIsTyped(scanner: Scanner): Program? {
        while (true) {
            echo("expr> ", trailingNewline = false)
            if (!scanner.hasNextLine()) return null

            try {
                return compile(scanner.nextLine())
            } catch (e: IllegalArgumentException) {
                echo("could not compile expression: ${e.message}")
            }
        }
    }

    private fun fail(message: String?): Nothing = throw CliktError(message)

    private companion object {
        const val FAILED = "failed"
    }
}

private fun readVariables(line: String): Map<String, Float> = line.split(";")
    .asSequence()
    .filterNot { it.isBlank() }
    .associate {
        val parts = it.split("=")
        require(parts.size == 2) { "'$it' is not an assignment" }
        parts[0].trim() to parts[1].trim().let { value ->
            requireNotNull(value.toFloatOrNull()) { "'$value' is not a number" }
        }
    }

fun main(args: Array<String>) = MathCommand().main(args)
