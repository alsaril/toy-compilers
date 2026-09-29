package com.alsaril.scheme.compiler

import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Printer.print
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class IfTest {

    @ParameterizedTest
    @CsvSource(
        "(if #t 0), 0",
        "(if #f 0), ()",
        "(if (= 2 2) (+ 1 10)), 11",
        "(if (= 2 3) (+ 1 10) 5), 5",
        quoteCharacter = '$'
    )
    fun `returns value from if`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `if evaluates properly`() {
        // given
        val env = GlobalEnvironment()
        compile("(define x 1)").run(env)

        // when / then
        compile("(if #f (set! x 2))").run(env)
        assertThat(compile("x").run(env).let(::print)).isEqualTo("1")

        // when / then
        compile("(if #t (set! x 4) (set! x 3))").run(env)
        assertThat(compile("x").run(env).let(::print)).isEqualTo("4")

        // then
        assertThat(compile("(if (< 1 2) (+ 4 -3) (-3 . 2))").run(env).let(::print)).isEqualTo("1")

        // then
        assertThat(compile("(if (>= -3 2) hello 'world)").run(env).let(::print)).isEqualTo("world")

        // then
        assertThatThrownBy { compile("(if (<= -3 2) hello 'world)").run(env) }
    }

    @ParameterizedTest
    @CsvSource(
        "(if)",
        "(if 1 2 3 4)",
        quoteCharacter = '$'
    )
    fun `throws on if syntax errors`(input: String) {
        // given
        val env = GlobalEnvironment()

        // when / then
        assertThatThrownBy { compile(input).run(env) }
    }
}
