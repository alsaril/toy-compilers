package com.alsaril.scheme

import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.runtime.GlobalEnvironment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CompilerTest {
    @ParameterizedTest
    @CsvSource(
        "#t, TRUE",
        "#f, FALSE",
        "(boolean? #t), TRUE",
        "(boolean? #f), TRUE",
        "(boolean? 1), FALSE",
        "(boolean? '()), FALSE",
        "(boolean? ''#f), FALSE",
        "(not #f), TRUE",
        "(not #t), FALSE",
        "(not 1), FALSE",
        "(not 0), FALSE",
        "(not '()), FALSE",
        "(boolean? (not #f)), TRUE",
        "(boolean? (not 1)), TRUE",
//        "(= 1 1), TRUE",
//        "(= 0 1), FALSE",
//        "(> 1 1), FALSE",
//        "(> 10 1), TRUE",
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
