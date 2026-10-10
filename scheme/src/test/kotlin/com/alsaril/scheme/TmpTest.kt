package com.alsaril.scheme

import com.alsaril.codegen.Compiler.pipeline
import com.alsaril.scheme.analyser.Call
import com.alsaril.scheme.analyser.GlobalForm
import com.alsaril.scheme.analyser.NumberConstant
import com.alsaril.scheme.analyser.Reference
import com.alsaril.scheme.analyser.Reference.Location.GLOBAL
import com.alsaril.scheme.compiler.ClassGenerator
import com.alsaril.scheme.compiler.ClassGenerator.Companion.generate
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Printer.print
import com.alsaril.scheme.runtime.Program
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TmpTest {
    @Test
    fun test() {
        // given
        val ast = Call(Reference("+", GLOBAL, true, 0), listOf(NumberConstant(10), NumberConstant(42)), false)
        val refs = listOf(
            Reference("+", GLOBAL, true, 0),
        )
        val program = pipeline(
            "", { ast },
            { expression -> generate(GlobalForm(expression, refs)) }, Program::class.java, ClassGenerator.lookup
        )

        // when
        val result = program.run(GlobalEnvironment()).let(::print)

        // then
        assertThat(result).isEqualTo("52")
    }
}