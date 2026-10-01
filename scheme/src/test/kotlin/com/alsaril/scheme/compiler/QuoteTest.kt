package com.alsaril.scheme.compiler

import com.alsaril.scheme.assertSyntaxError
import com.alsaril.scheme.execute
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class QuoteTest {
    @ParameterizedTest
    @CsvSource(
        "'101, 101",
        "'x, x",
        "(quote x), x",
        "'(), ()",
        "'(1), (1)",
        "'(1 2), (1 2)",
        "(quote (1 2)), (1 2)",
        "'(1 . 2), (1 . 2)",
        "(quote (-2 . 3)), (-2 . 3)",
        "'(1 2 . 3), (1 2 . 3)",
        "'(1 2 . ()), (1 2)",
        "'(1 . (2 . ())), (1 2)",
        "''x, (quote x)",
        "'(and 1 2 'c '(f g)), (and 1 2 (quote c) (quote (f g)))",
        quoteCharacter = '$'
    )
    fun `quote returns its operand unevaluated`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(quote) | quote: expected 1 operand, got 0 in (quote)",
        "(quote 1 2) | quote: expected 1 operand, got 2 in (quote 1 2)",
        "(quote . 1) | expected a proper list of operands in (quote . 1)",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `rejects malformed quote`(input: String, message: String) {
        assertSyntaxError(input, message)
    }
}
