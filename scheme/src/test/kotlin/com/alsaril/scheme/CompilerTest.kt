package com.alsaril.scheme

import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.runtime.GlobalEnvironment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CompilerTest {
    @ParameterizedTest
    @CsvSource(
        "#t, true",
        "#f, false",
        "(boolean? #t), true",
        "(boolean? #f), true",
        "(boolean? 1), false",
        "(boolean? '()), false",
        "(boolean? ''#f), false",
        "(not #f), true",
        "(not #t), false",
        "(not 1), false",
        "(not 0), false",
        "(not '()), false",
        "(boolean? (not #f)), true",
        "(boolean? (not 1)), true",
        "(= 1 1), true",
        "(= 0 1), false",
        "(< 1 1), false",
        "(< 1 10), true",
        "(> 1 1), false",
        "(> 10 1), true",
        quoteCharacter = '$'
    )
    fun `executes a simple expression`(input: String, expected: Boolean) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env)

        // then
        assertThat(result).isEqualTo(expected)
    }
}
