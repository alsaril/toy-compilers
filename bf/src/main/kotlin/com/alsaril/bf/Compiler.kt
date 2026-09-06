package com.alsaril.bf

import com.alsaril.bf.CodeGenerator.generate
import com.alsaril.bf.ir.IrInstruction
import com.alsaril.bf.ir.IrVisitor
import com.alsaril.codegen.ByteClassLoader.loadClass
import org.antlr.v4.runtime.BailErrorStrategy
import org.antlr.v4.runtime.CharStreams.fromString
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.misc.ParseCancellationException


object Compiler {
    fun compile(expr: String): ExtendedRunnable {
        val instructions = parse(expr) // frontend
        val (name, code) = generate(instructions) // backend
        val clazz = loadClass(name, code)
        require(ExtendedRunnable::class.java.isAssignableFrom(clazz)) // sanity check
        return clazz.getDeclaredConstructor().newInstance() as ExtendedRunnable
    }

    private fun parse(expr: String): List<IrInstruction> {
        val lexer = BFLexer(fromString(expr))
        val parser = BFParser(CommonTokenStream(lexer))
            .apply { errorHandler = BailErrorStrategy() }
        val ast = try {
            parser.expr()
        } catch (e: ParseCancellationException) {
            throw IllegalArgumentException(describe(e.cause as? RecognitionException), e)
        }
        val instructions = IrVisitor().apply { visit(ast) }.instructions()
        return instructions
    }

    private fun describe(cause: RecognitionException?): String {
        val token = cause?.offendingToken ?: return "the program cannot be parsed"
        val at = "line ${token.line}, column ${token.charPositionInLine + 1}"

        // the grammar only fails on brackets, so an unexpected end means one is open
        return when (token.type) {
            Token.EOF -> "unexpected end of program at $at, a '[' is never closed"
            else -> "unexpected '${token.text}' at $at"
        }
    }
}