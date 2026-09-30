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

    @ParameterizedTest
    @CsvSource(
        "(define x (+ 1 2)), x, 3",
        "(define x (+ 2 -4)), x, -2",
        quoteCharacter = '$'
    )
    fun `symbols as variable names`(input: String, name: String, expected: String) {
        // given
        val env = GlobalEnvironment()
        compile(input).run(env)

        // when
        val result = compile(name).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `set overrides variables`() {
        // given
        val env = GlobalEnvironment()

        assertThatThrownBy { compile("(set! x 2)").run(env) }
        assertThatThrownBy { compile("x").run(env) }

        compile("(define x 1)").run(env)
        assertThat(compile("x").run(env).let(::print)).isEqualTo("1")

        compile("(set! x (+ 2 4))").run(env)
        assertThat(compile("x").run(env).let(::print)).isEqualTo("6")

        compile("(set! x '(+ 2 (min -4 3)))").run(env)
        assertThat(compile("x").run(env).let(::print)).isEqualTo("(+ 2 (min -4 3))")
    }

    @ParameterizedTest
    @CsvSource(
        "(define)",
        "(define 1)",
        "(define x 1 2)",
        "(set!)",
        "(set! 1)",
        "(set! x 1 2)",
        quoteCharacter = '$'
    )
    fun `throws on invalid syntax`(input: String) {
        // given
        val env = GlobalEnvironment()

        // when / then
        assertThatThrownBy { compile(input).run(env) }
    }
}
