package com.alsaril.bf

import com.alsaril.bf.generator.ClassGenerator
import com.alsaril.codegen.Compiler.pipeline

object BfCompiler {
    fun compile(expr: String) = pipeline(
        expr,
        Parser::parse,
        ClassGenerator::generate,
        Program::class.java
    )
}