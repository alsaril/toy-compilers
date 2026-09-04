package com.alsaril.bf

import com.alsaril.bf.CodeGenerator.generate
import com.alsaril.bf.ir.IrInstruction
import com.alsaril.bf.ir.IrVisitor
import com.alsaril.codegen.ByteClassLoader.loadClass
import org.antlr.v4.runtime.CharStreams.fromString
import org.antlr.v4.runtime.CommonTokenStream


object Compiler {
    fun compile(expr: String): Runnable {
        val instructions = parse(expr) // frontend
        val (name, code) = generate(instructions) // backend
        val clazz = loadClass(name, code)
        require(Runnable::class.java.isAssignableFrom(clazz)) // sanity check
        return clazz.getDeclaredConstructor().newInstance() as Runnable
    }

    private fun parse(expr: String): List<IrInstruction> {
        val lexer = BFLexer(fromString(expr))
        val parser = BFParser(CommonTokenStream(lexer))
        val ast = parser.expr()
        val instructions = IrVisitor().apply { visit(ast) }.instructions()
        return instructions
    }
}