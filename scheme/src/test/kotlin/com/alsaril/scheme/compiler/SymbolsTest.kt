package com.alsaril.scheme.compiler

import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Printer.print
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SymbolsTest {
    @ParameterizedTest
    @CsvSource(
        "'x, x",
        "(quote x), x",
        quoteCharacter = '$'
    )
    fun `quoted symbols`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `doesn't evaluate symbols`() {
        // given
        val env = GlobalEnvironment()

        // when / then
        assertThatThrownBy { compile("x").run(env) }
    }

    @ParameterizedTest
    @CsvSource(
        "(symbol? 'x), #t",
        "(symbol? '+), #t",
        "(symbol? +), #f",
        "(symbol? '<=>-end), #t",
        "(symbol? 1), #f",
        "(symbol? #t), #f",
        "(symbol? ''x), #f",
        "(symbol? 'symbol?), #t",
        quoteCharacter = '$'
    )
    fun `symbols predicate`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }
}
