package com.alsaril.scheme.compiler

import com.alsaril.scheme.assertRuntimeError
import com.alsaril.scheme.execute
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class IntegerTest {
    @ParameterizedTest
    @CsvSource(
        "4, 4",
        "-14, -14",
        "+14, 14",
        quoteCharacter = '$'
    )
    fun `integers evaluate to themselves`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(number? -1), #t",
        "(number? 1), #t",
        "(number? #t), #f",
        "(number? (- 2 3)), #t",
        "(number? '(/ 2 -1)), #f",
        "(number? '()), #f",
        quoteCharacter = '$'
    )
    fun `number? recognizes integers`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
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
    fun `compares integers`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(= 1 #t) | =: expected a number, got #t",
        "(< 1 #t) | <: expected a number, got #t",
        "(> 1 #t) | >: expected a number, got #t",
        "(<= 1 #t) | <=: expected a number, got #t",
        "(>= 1 #t) | >=: expected a number, got #t",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `fails on comparing non-numbers`(input: String, message: String) {
        assertRuntimeError(input, message)
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
        "(+), 0",
        "(*), 1",
        quoteCharacter = '$'
    )
    fun `does integer arithmetic`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(+ 1 #t) | +: expected a number, got #t",
        "(- 1 #t) | -: expected a number, got #t",
        "(* 1 #t) | *: expected a number, got #t",
        "(/ 1 #t) | /: expected a number, got #t",
        "(/) | /: expected at least 1 argument, got 0",
        "(-) | -: expected at least 1 argument, got 0",
        "(/ 1 0) | /: division by zero",
        "(/ 6 3 0) | /: division by zero",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `fails on invalid arithmetic`(input: String, message: String) {
        assertRuntimeError(input, message)
    }

    @ParameterizedTest
    @CsvSource(
        "(max 3), 3",
        "(min 3), 3",
        "(max 1 2), 2",
        "(min 1 2), 1",
        "(max 1 -2 5 3 4), 5",
        "(min 1 2 3 -4 5), -4",
        quoteCharacter = '$'
    )
    fun `finds max and min`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(max) | max: expected at least 1 argument, got 0",
        "(min) | min: expected at least 1 argument, got 0",
        "(max #t) | max: expected a number, got #t",
        "(min #t) | min: expected a number, got #t",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `fails on invalid max and min`(input: String, message: String) {
        assertRuntimeError(input, message)
    }

    @ParameterizedTest
    @CsvSource(
        "(abs 10), 10",
        "(abs -10), 10",
        quoteCharacter = '$'
    )
    fun `takes the absolute value`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(abs) | abs: expected 1 argument, got 0",
        "(abs #t) | abs: expected a number, got #t",
        "(abs 1 2) | abs: expected 1 argument, got 2",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `fails on invalid abs`(input: String, message: String) {
        assertRuntimeError(input, message)
    }
}
