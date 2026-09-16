package com.alsaril.scheme

import com.alsaril.scheme.tokenizer.Number
import com.alsaril.scheme.tokenizer.Tokenizer.tokenize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class TokenizerTest {
    @ParameterizedTest
    @CsvSource(
        "'5', 5",
        "'-5', -5",
        "' 5001', 5001",
        "' -90 ', -90"
    )
    fun `reads numbers`(input: String, expected: Int) {
        // when
        val output = tokenize(input)

        // then
        assertThat(output).containsOnly(Number(expected))
    }
}
