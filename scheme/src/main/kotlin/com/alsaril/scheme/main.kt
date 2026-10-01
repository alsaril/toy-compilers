package com.alsaril.scheme

import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.runtime.Environment
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Printer.print
import com.alsaril.scheme.runtime.Unspecified
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.output.MordantHelpFormatter
import java.util.*

internal class SchemeCommand : CliktCommand(name = "scheme") {

    init {
        context { helpFormatter = { MordantHelpFormatter(it, showDefaultValues = true) } }
    }

    override fun help(context: Context) =
        "Read Scheme expressions a line at a time, compile each to a JVM class and run it, until the input runs out"

    override fun run() {
        val environment = GlobalEnvironment()
        val scanner = Scanner(System.`in`)

        while (true) {
            echo(PROMPT, trailingNewline = false)
            if (!scanner.hasNextLine()) return

            val line = scanner.nextLine()
            if (line.isNotBlank()) evaluate(line, environment)?.let(::echo)
        }
    }

    private fun evaluate(line: String, environment: Environment): String? {
        val program = try {
            compile(line)
        } catch (e: SchemeSyntaxException) {
            return "syntax error: ${e.message}"
        } catch (_: StackOverflowError) {
            return "compile error: stack overflow"
        }

        return try {
            program.run(environment).takeUnless { it == Unspecified }?.let(::print)
        } catch (e: SchemeNameException) {
            "name error: ${e.message}"
        } catch (e: SchemeRuntimeException) {
            "runtime error: ${e.message}"
        } catch (_: StackOverflowError) {
            "runtime error: stack overflow"
        }
    }

    private companion object {
        const val PROMPT = "scheme> "
    }
}

fun main(args: Array<String>) = SchemeCommand().main(args)
