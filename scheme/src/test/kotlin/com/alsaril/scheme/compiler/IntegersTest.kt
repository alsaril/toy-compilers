package com.alsaril.scheme.compiler

import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Printer.print
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class IntegersTest {
    @ParameterizedTest
    @CsvSource(
        "4, 4",
        "-14, -14",
        "+14, 14",
        quoteCharacter = '$'
    )
    fun `self evaluates an integer`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(number? -1), #t",
        "(number? 1), #t",
        "(number? #t), #f",
        quoteCharacter = '$'
    )
    fun `number predicate`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(=), #t",
        "(>), #t",
        "(<), #t",
        "(>=), #t",
        "(<=), #t",
        "(= 1 2), #f",
        "(= 1 1), #t",
        "(= 1 1 1), #t",
        "(= 1 1 2), #f",
        "(= -14 -14), #t",
        "(> 2 1), #t",
        "(> 1 1), #f",
        "(> 3 2 1), #t",
        "(> -1 -2 -3), #t",
        "(> 3 2 3), #f",
        "(< 1 2), #t",
        "(< 1 1), #f",
        "(< 1 2 3), #t",
        "(< 1 2 1), #f",
        "(>= 2 1), #t",
        "(>= 1 2), #f",
        "(>= 3 3 2), #t",
        "(>= 3 3 4), #f",
        "(<= 2 1), #f",
        "(<= 1 2), #t",
        "(<= 3 3 4), #t",
        "(<= 3 3 2), #f",
        quoteCharacter = '$'
    )
    fun `integer comparison`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(= 1 #t)",
        "(< 1 #t)",
        "(> 1 #t)",
        "(<= 1 #t)",
        "(>= 1 #t)",
        quoteCharacter = '$'
    )
    fun `rejects malformed comparisons`(input: String) {
        // given
        val env = GlobalEnvironment()

        // when / then
        assertThatThrownBy { compile(input).run(env) }
    }

    @ParameterizedTest
    @CsvSource(
        "(+ 1 2), 3",
        "(+ 1), 1",
        "(+ 1 (+ 3 4 5)), 13",
        "(- 1 2), -1",
        "(- -10 -20), 10",
        "(- -5 -3 0 -1), -1",
        "(- 2 1), 1",
        "(* -5 6), -30",
        "(/ 4 2), 2",
        "(/ 16 -4 2), -2",
        "(/ -90 -3 -5), -6",
        "(/ -91 (/ 7 -3)), 45",
        "(+ 34 (/ -56 23) (* 1 2 (- 5 10))), 22",
        quoteCharacter = '$'
    )
    fun `integer arithmetic`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }
}
