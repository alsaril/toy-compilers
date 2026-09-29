package com.alsaril.scheme.compiler

import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Printer.print
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class BooleansTest {
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
        "(= 1 1), #t",
        "(= 0 1), #f",
        "(< 1 1), #f",
        "(< 1 10), #t",
        "(> 1 1), #f",
        "(> 10 1), #t",
        quoteCharacter = '$'
    )
    fun `executes a simple expression`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
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
        "(boolean? (or #f #f #f)), #t",
        "(boolean? (or #f #f -15)), #f",
        "(or #f #t (1 2)), #t",
        quoteCharacter = '$'
    )
    fun `executes and or`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(not)",
        "(not #t #t)",
        quoteCharacter = '$'
    )
    fun `throws on invalid calls`(input: String) {
        // given
        val env = GlobalEnvironment()

        // when / then
        val program = compile(input)
        assertThatThrownBy { program.run(env) }
    }

    @Test
    fun `and optimizes argument evaluation`() {
        // given
        val env = GlobalEnvironment()

        // when
        compile("(define x 1)").run(env)
        compile("(and #f (set! x 2))").run(env)

        // then
        assertThat(compile("x").run(env).let(::print)).isEqualTo("1")
        assertThatThrownBy { compile("(and #t #t (1 2))").run(env) }
    }

    @Test
    fun `or optimizes argument evaluation`() {
        // given
        val env = GlobalEnvironment()

        // when
        compile("(define x 1)").run(env)
        compile("(or #t (set! x 2))").run(env)

        // then
        assertThat(compile("x").run(env).let(::print)).isEqualTo("1")
        assertThatThrownBy { compile("(or #f #f (1 2))").run(env) }
    }
}