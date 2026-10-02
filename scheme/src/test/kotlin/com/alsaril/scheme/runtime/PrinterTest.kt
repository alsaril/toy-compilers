package com.alsaril.scheme.runtime

import com.alsaril.scheme.execute
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PrinterTest {
    @ParameterizedTest
    @CsvSource(
        "#t, #t",
        "#f, #f",
        "-5, -5",
        "'x, x",
        "'(), ()",
        "'(() ()), (() ())",
        "'(1 . 2), (1 . 2)",
        "'(1 (2 (3)) . 4), (1 (2 (3)) . 4)",
        "(if #f #f), #<unspecified>",
        "car, #<procedure>",
        "(lambda (x) x), #<procedure>",
        "(list car (lambda () 1)), (#<procedure> #<procedure>)",
        quoteCharacter = '$'
    )
    fun `prints every kind of value`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }
}
