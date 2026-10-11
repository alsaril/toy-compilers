package com.alsaril.scheme

import com.alsaril.codegen.Compiler.pipeline
import com.alsaril.scheme.analyser.*
import com.alsaril.scheme.analyser.ResolvedReference.Location.ARGUMENT
import com.alsaril.scheme.analyser.ResolvedReference.Location.CAPTURE
import com.alsaril.scheme.analyser.ResolvedReference.Location.GLOBAL
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
        val ast = Call(
            Lambda(
                listOf("x", "y"),
                listOf(

                    Call(Lambda(
                        listOf(),
                        listOf(
                            Reference("y")
                        ),
                        Frame(listOf(ResolvedReference("y", CAPTURE, false, 1)))
                    ), listOf(/*Reference("x"), Reference("y")*/))

                ),
                Frame(
                    listOf(
                        ResolvedReference("+", GLOBAL, true, 0),
                        ResolvedReference("x", ARGUMENT, false, 1),
                        ResolvedReference("y", ARGUMENT, false, 2),
                    )
                )
            ), listOf(NumberConstant(10), NumberConstant(20))
        )
        val refs = listOf(
            ResolvedReference("+", GLOBAL, true, 0),
        )

        val program = pipeline(
            "", { null },
            { generate(GlobalForm(ast, Frame(refs))) }, Program::class.java, ClassGenerator.lookup
        )

        // when
        val result = program.run(GlobalEnvironment()).let(::print)

        // then
        assertThat(result).isEqualTo("10")
    }
}