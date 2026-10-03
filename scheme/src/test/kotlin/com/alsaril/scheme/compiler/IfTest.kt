package com.alsaril.scheme.compiler

import com.alsaril.scheme.assertNameError
import com.alsaril.scheme.assertSyntaxError
import com.alsaril.scheme.execute
import com.alsaril.scheme.runtime.GlobalEnvironment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class IfTest {
    @ParameterizedTest
    @CsvSource(
        "(if #t 0), 0",
        "(if #f 0), #<unspecified>",
        "(if (= 2 2) (+ 1 10)), 11",
        "(if (= 2 3) (+ 1 10) 5), 5",
        "(if 0 1 2), 1",
        "(if '() 1 2), 1",
        "(if '#f 1 2), 2",
        quoteCharacter = '$'
    )
    fun `returns the value of the branch taken`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @Test
    fun `evaluates only the branch taken`() {
        // given
        val env = GlobalEnvironment()
        execute("(define x 1)", env)

        // when / then
        execute("(if #f (set! x 2))", env)
        assertThat(execute("x", env)).isEqualTo("1")

        // when / then
        execute("(if #t (set! x 4) (set! x 3))", env)
        assertThat(execute("x", env)).isEqualTo("4")

        // when / then
        assertThat(execute("(if (< 1 2) (+ 4 -3) (-3 2))")).isEqualTo("1")
        assertThat(execute("(if (>= -3 2) hello 'world)")).isEqualTo("world")
        assertNameError("(if (<= -3 2) hello 'world)", "hello is not defined")
    }

    @ParameterizedTest
    @CsvSource(
        "(if) | if: expected 2 or 3 operands, got 0 in (if)",
        "(if 1) | if: expected 2 or 3 operands, got 1 in (if 1)",
        "(if 1 2 3 4) | if: expected 2 or 3 operands, got 4 in (if 1 2 3 4)",
        "(if #t . 1) | expected a proper list of operands in (if #t . 1)",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `rejects malformed if`(input: String, message: String) {
        assertSyntaxError(input, message)
    }
}
