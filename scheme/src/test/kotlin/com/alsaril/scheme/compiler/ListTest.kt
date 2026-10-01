package com.alsaril.scheme.compiler

import com.alsaril.scheme.assertRuntimeError
import com.alsaril.scheme.assertSyntaxError
import com.alsaril.scheme.execute
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ListTest {
    @ParameterizedTest
    @CsvSource(
        "() | () is not an expression, quote it as '() for the empty list",
        "(f ()) | () is not an expression, quote it as '() for the empty list",
        "(list 1 . 2) | expected a proper list of operands in (list 1 . 2)",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `rejects empty and dotted combinations`(input: String, message: String) {
        assertSyntaxError(input, message)
    }

    @ParameterizedTest
    @CsvSource(
        "(1) | 1 is not a procedure",
        "(1 2 3) | 1 is not a procedure",
        "('f 1) | f is not a procedure",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `fails on calling a non-procedure`(input: String, message: String) {
        assertRuntimeError(input, message)
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
    fun `pair? recognizes pairs`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(null? '()), #t",
        "(null? '(1 2)), #f",
        "(null? '(1 . 2)), #f",
        "(null? (if #f #f)), #f",
        quoteCharacter = '$'
    )
    fun `null? recognizes the empty list`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(list? '()), #t",
        "(list? '(1 2)), #t",
        "(list? '(1 . 2)), #f",
        "(list? '(1 2 3 4 . 5)), #f",
        quoteCharacter = '$'
    )
    fun `list? recognizes proper lists`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(cons 1 2), (1 . 2)",
        "(cons 1 '(2)), (1 2)",
        "(car '(1 . 2)), 1",
        "(cdr '(1 . 2)), 2",
        quoteCharacter = '$'
    )
    fun `builds and takes apart pairs`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(list), ()",
        "(list 1), (1)",
        "(list 1 (+ 1 1) 3), (1 2 3)",
        "(list-ref '(1 2 3) 1), 2",
        "(list-tail '(1 2 3) 1), (2 3)",
        "(list-tail '(1 2 3) 3), ()",
        quoteCharacter = '$'
    )
    fun `builds and indexes lists`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(list-ref '(1 2 3) 3) | list-ref: index 3 is out of range for (1 2 3)",
        "(list-ref '(1 2 3) 10) | list-ref: index 10 is out of range for (1 2 3)",
        "(list-ref '(1 2 3) -1) | list-ref: expected a non-negative index, got -1",
        "(list-ref '(1 2 3) #t) | list-ref: expected a number, got #t",
        "(list-tail '(1 2 3) 10) | list-tail: index 10 is out of range for (1 2 3)",
        "(list-tail '(1 2 3) -1) | list-tail: expected a non-negative index, got -1",
        "(car 5) | car: expected a pair, got 5",
        "(cdr '()) | cdr: expected a pair, got ()",
        "(car) | car: expected 1 argument, got 0",
        "(cons 1) | cons: expected 2 arguments, got 1",
        "(cons 1 2 3) | cons: expected 2 arguments, got 3",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `fails on invalid list operations`(input: String, message: String) {
        assertRuntimeError(input, message)
    }
}
