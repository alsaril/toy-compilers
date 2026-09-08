package com.alsaril.math

import com.alsaril.codegen.Compiler.pipeline

object MathCompiler {
    fun compile(expr: String) = pipeline(
        expr,
        Parser::parse,
        ClassGenerator::generate,
        Program::class.java
    )
}