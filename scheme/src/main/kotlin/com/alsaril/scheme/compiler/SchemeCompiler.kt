package com.alsaril.scheme.compiler

import com.alsaril.codegen.Compiler.pipeline
import com.alsaril.scheme.Procedure
import com.alsaril.scheme.compiler.ClassGenerator.generate
import com.alsaril.scheme.parser.Parser.parse
import com.alsaril.scheme.runtime.TempGlobalContext
import com.alsaril.scheme.tokenizer.Tokenizer.tokenize

class SchemeCompiler {
    private val globalContext = TempGlobalContext()

    fun compile(expr: String) = pipeline(
        expr,
        { tokenize(it).let(::parse) },
        ::generate,
        Procedure::class.java,
        globalContext,
    )
}