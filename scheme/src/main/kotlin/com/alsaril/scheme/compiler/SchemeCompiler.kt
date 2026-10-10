package com.alsaril.scheme.compiler

import com.alsaril.codegen.Compiler.pipeline
import com.alsaril.scheme.analyser.Analyser.analyse
import com.alsaril.scheme.compiler.ClassGenerator.Companion.generate
import com.alsaril.scheme.parser.Parser.parse
import com.alsaril.scheme.runtime.Program
import com.alsaril.scheme.tokenizer.Tokenizer.tokenize

object SchemeCompiler {
    fun compile(expr: String) = pipeline(
        expr,
        { tokenize(it).let(::parse).let(::analyse) },
        ::generate,
        Program::class.java,
        ClassGenerator.lookup,
    )
}