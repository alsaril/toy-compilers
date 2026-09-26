package com.alsaril.scheme

import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Printer.print
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PrinterTest {
    @ParameterizedTest
    @CsvSource(
        "'(and), (and)",
        "'(and (= 2 2) (> 2 1)), (and (= 2 2) (> 2 1))",
        "'(and (= 2 2) (< 2 1)), (and (= 2 2) (< 2 1))",
        "'(and 1 2 'c '(f g)), (and 1 2 (quote c) (quote (f g)))",
        "'(boolean? (and #t #f #t)), (boolean? (and #t #f #t))",
        "'(boolean? (and #t #t '4)), (boolean? (and #t #t (quote 4)))",
        quoteCharacter = '$'
    )
    fun `prints`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }
}
