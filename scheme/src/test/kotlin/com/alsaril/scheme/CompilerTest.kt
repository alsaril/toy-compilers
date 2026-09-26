package com.alsaril.scheme

import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Printer.print
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CompilerTest {
    @ParameterizedTest
    @CsvSource(
        "#t, #t",
        "#f, #f",
        "(boolean? #t), #t",
        "(boolean? #f), #t",
        "(boolean? 1), #f",
        "(boolean? '()), #f",
        "(boolean? ''#f), #f",
        "(not #f), #t",
        "(not #t), #f",
        "(not 1), #f",
        "(not 0), #f",
        "(not '()), #f",
        "(boolean? (not #f)), #t",
        "(boolean? (not 1)), #t",
        "(= 1 1), #t",
        "(= 0 1), #f",
        "(< 1 1), #f",
        "(< 1 10), #t",
        "(> 1 1), #f",
        "(> 10 1), #t",
        quoteCharacter = '$'
    )
    fun `executes a simple expression`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(and), #t",
        "(and (= 2 2) (> 2 1)), #t",
        "(and (= 2 2) (< 2 1)), #f",
        "(and 1 2 'c '(f g)), (f g)",
        "(boolean? (and #t #f #t)), #t",
        "(boolean? (and #t #t '4)), #f",
        quoteCharacter = '$'
    )
    fun `executes and or`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }
}
