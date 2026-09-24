package com.alsaril.scheme.compiler

import com.alsaril.codegen.Compiler.pipeline
import com.alsaril.scheme.compiler.ClassGenerator.generate
import com.alsaril.scheme.parser.Parser.parse
import com.alsaril.scheme.runtime.Program
import com.alsaril.scheme.tokenizer.Tokenizer.tokenize

object SchemeCompiler {
    fun compile(expr: String) = pipeline(
        expr,
        { tokenize(it).let(::parse) },
        ::generate,
        Program::class.java,
    )
}