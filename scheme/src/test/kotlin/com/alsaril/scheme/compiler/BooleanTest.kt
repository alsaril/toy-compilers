package com.alsaril.scheme.compiler

import com.alsaril.scheme.assertRuntimeError
import com.alsaril.scheme.assertSyntaxError
import com.alsaril.scheme.execute
import com.alsaril.scheme.runtime.GlobalEnvironment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class BooleanTest {
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
        quoteCharacter = '$'
    )
    fun `evaluates booleans, boolean? and not`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(and), #t",
        "(and 1), 1",
        "(and (= 2 2) (> 2 1)), #t",
        "(and (= 2 2) (< 2 1)), #f",
        "(and 1 2 'c '(f g)), (f g)",
        "(boolean? (and #t #f #t)), #t",
        "(boolean? (and #t #t '4)), #f",
        "(and #t #f (1 2)), #f",
        "(or), #f",
        "(or 1), 1",
        "(or (not (= 2 2)) (> 2 1)), #t",
        "(or #f (< 2 1)), #f",
        "(or #f 1), 1",
        "(or 2 #f), 2",
        "(or 2 3), 2",
        "(or #f 2 #f), 2",
        "(or #f #f), #f",
        "(boolean? (or #f #f #f)), #t",
        "(boolean? (or #f #f -15)), #f",
        "(or #f #t (1 2)), #t",
        quoteCharacter = '$'
    )
    fun `and and or return the deciding operand`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(not) | not: expected 1 argument, got 0",
        "(not #t #t) | not: expected 1 argument, got 2",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `fails on wrong argument count to not`(input: String, message: String) {
        assertRuntimeError(input, message)
    }

    @ParameterizedTest
    @CsvSource(
        "(and 1 . 2) | expected a proper list of operands in (and 1 . 2)",
        "(or 1 . 2) | expected a proper list of operands in (or 1 . 2)",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `rejects dotted and and or`(input: String, message: String) {
        assertSyntaxError(input, message)
    }

    @Test
    fun `and stops at the first false operand`() {
        // given
        val env = GlobalEnvironment()
        execute("(define x 1)", env)

        // when
        execute("(and #f (set! x 2))", env)

        // then
        assertThat(execute("x", env)).isEqualTo("1")
        assertRuntimeError("(and #t #t (1 2))", "1 is not a procedure")
    }

    @Test
    fun `or stops at the first true operand`() {
        // given
        val env = GlobalEnvironment()
        execute("(define x 1)", env)

        // when
        execute("(or #t (set! x 2))", env)

        // then
        assertThat(execute("x", env)).isEqualTo("1")
        assertRuntimeError("(or #f #f (1 2))", "1 is not a procedure")
    }
}
