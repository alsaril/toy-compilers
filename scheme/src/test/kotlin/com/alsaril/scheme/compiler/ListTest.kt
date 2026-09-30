package com.alsaril.scheme.compiler

import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Printer.print
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ListTest {

    @ParameterizedTest
    @CsvSource(
        "()",
        "(1)",
        "(1 2 3)",
        quoteCharacter = '$'
    )
    fun `lists are not self-evaluating`(input: String) {
        // given
        val env = GlobalEnvironment()

        // when / then
        assertThatThrownBy { compile(input).run(env) }
    }

    @ParameterizedTest
    @CsvSource(
        "'(), ()",
        "'(1), (1)",
        "'(1 2), (1 2)",
        quoteCharacter = '$'
    )
    fun `quoted lists`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "((1)",
        "(1))",
        ")(1)",
        "(.)",
        "(1 .)",
        "(. 2)",
        "(1 . 2 3)",
        quoteCharacter = '$'
    )
    fun `invalid syntax`(input: String) { // should throw syntax exception
        // given
        val env = GlobalEnvironment()

        // when / then
        assertThatThrownBy { compile(input).run(env) }
    }

    @ParameterizedTest
    @CsvSource(
        "'(1 . 2), (1 . 2)",
        "'(1 2 . 3), (1 2 . 3)",
        "'(1 2 . ()), (1 2)",
        "'(1 . (2 . ())), (1 2)",
        quoteCharacter = '$'
    )
    fun `list syntax`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(pair? '(1 . 2)), #t",
        "(pair? '(1 2)), #t",
        "(pair? '(-3 -1 2)), #t",
        "(pair? '(not #f)), #t",
        "(pair? '()), #f",
        "(pair? #t), #f",
        quoteCharacter = '$'
    )
    fun `pair predicate`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(null? '()), #t",
        "(null? '(1 2)), #f",
        "(null? '(1 . 2)), #f",
        quoteCharacter = '$'
    )
    fun `null predicate`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(list? '()), #t",
        "(list? '(1 2)), #t",
        "(list? '(1 . 2)), #f",
        "(list? '(1 2 3 4 . 5)), #f",
        quoteCharacter = '$'
    )
    fun `list predicate`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(cons 1 2), (1 . 2)",
        "(car '(1 . 2)), 1",
        "(cdr '(1 . 2)), 2",
        quoteCharacter = '$'
    )
    fun `pair operations`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }
}
