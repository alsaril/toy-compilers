package com.alsaril.scheme

import com.alsaril.scheme.parser.Number
import com.alsaril.scheme.parser.Parser.parse
import com.alsaril.scheme.tokenizer.Tokenizer.tokenize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class ParserTest {
    @ParameterizedTest
    @MethodSource("simple")
    fun `parses numbers`(input: String, expected: Int) {
        // when
        val output = parse(tokenize(input))

        // then
        assertThat(output).isEqualTo(Number(expected))
    }

    companion object {
        @JvmStatic
        fun simple() = listOf(
            arguments("5", 5)
        )
    }
}