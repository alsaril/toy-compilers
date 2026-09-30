package com.alsaril.scheme.compiler

import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Printer.print
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class EvalTest {
    @ParameterizedTest
    @CsvSource(
        "(quote (1 2)), (1 2)",
        "'(1 2), (1 2)",
        "'101, 101",
        "(quote (-2 . 3)), (-2 . 3)",
        quoteCharacter = '$'
    )
    fun `quote`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }
}
