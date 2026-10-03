package com.alsaril.scheme.compiler

import com.alsaril.scheme.assertNameError
import com.alsaril.scheme.assertSyntaxError
import com.alsaril.scheme.execute
import com.alsaril.scheme.runtime.GlobalEnvironment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SymbolTest {
    @ParameterizedTest
    @CsvSource(
        "(symbol? 'x), #t",
        "(symbol? '+), #t",
        "(symbol? +), #f",
        "(symbol? '<=>-end), #t",
        "(symbol? 1), #f",
        "(symbol? #t), #f",
        "(symbol? ''x), #f",
        "(symbol? 'symbol?), #t",
        "(symbol? 'if), #t",
        "(symbol? '#t), #f",
        quoteCharacter = '$'
    )
    fun `symbol? recognizes symbols`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(define x (+ 1 2)), 3",
        "(define x (+ 2 -4)), -2",
        "(define x 'y), y",
        quoteCharacter = '$'
    )
    fun `define binds a symbol`(definition: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = execute(definition, env)

        // then
        assertThat(result).isEqualTo("#<unspecified>")
        assertThat(execute("x", env)).isEqualTo(expected)
    }

    @Test
    fun `set! changes a defined variable only`() {
        // given
        val env = GlobalEnvironment()

        // when / then
        assertNameError("(set! x 2)", "set!: x is not defined", env)
        assertNameError("x", "x is not defined", env)

        execute("(define x 1)", env)
        assertThat(execute("x", env)).isEqualTo("1")

        execute("(set! x (+ 2 4))", env)
        assertThat(execute("x", env)).isEqualTo("6")

        execute("(set! x '(+ 2 (min -4 3)))", env)
        assertThat(execute("x", env)).isEqualTo("(+ 2 (min -4 3))")
    }

    @ParameterizedTest
    @CsvSource(
        "(define) | define: expected 2 operands, got 0 in (define)",
        "(define 1) | define: expected 2 operands, got 1 in (define 1)",
        "(define x 1 2) | define: expected 2 operands, got 3 in (define x 1 2)",
        "(define 1 2) | define: expected a symbol, got 1 in (define 1 2)",
        "(set!) | set!: expected 2 operands, got 0 in (set!)",
        "(set! 1) | set!: expected 2 operands, got 1 in (set! 1)",
        "(set! x 1 2) | set!: expected 2 operands, got 3 in (set! x 1 2)",
        "(set! 1 2) | set!: expected a symbol, got 1 in (set! 1 2)",
        "(define if 1) | define: expected a symbol, got if in (define if 1)",
        "(define #t 1) | define: expected a symbol, got #t in (define #t 1)",
        "(set! and 1) | set!: expected a symbol, got and in (set! and 1)",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `rejects malformed define and set!`(input: String, message: String) {
        assertSyntaxError(input, message)
    }

    @ParameterizedTest
    @CsvSource(
        "if | if is a special form, not a value",
        "(list lambda) | lambda is a special form, not a value",
        "(define x set!) | set! is a special form, not a value",
        "(if #t quote 1) | quote is a special form, not a value",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `rejects a special form name as a value`(input: String, message: String) {
        assertSyntaxError(input, message)
    }
}
