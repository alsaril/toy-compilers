package com.alsaril.scheme.compiler

import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Printer.print
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LambdaTest {

    @ParameterizedTest
    @CsvSource(
        "((lambda () (+ 5 6 7))), 18",
        "((lambda (x) (+ 1 x)) 5), 6",
        "((lambda (x) (set! x (- 0 x)) x) 15), -15",
        "((lambda (x y) (define z (+ x y)) (* x y z)) 5 -3), -30",
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
    fun `invokes implicit begin`() {
        // given
        val env = GlobalEnvironment()
        compile("(define test (lambda (x) (set! x (* x 2)) (+ 1 x)))").run(env)

        // when
        val result = compile("(test 20)").run(env).let(::print)

        // then
        assertThat(result).isEqualTo("41")
    }

    @Test
    fun `slow sum`() {
        // given
        val env = GlobalEnvironment()
        compile("(define slow-add (lambda (x y) (if (= x 0) y (slow-add (- x 1) (+ y 1)))))").run(env)

        // when / then
        assertThat(compile("(slow-add 3 3)").run(env).let(::print)).isEqualTo("6")
        assertThat(compile("(slow-add 100 100)").run(env).let(::print)).isEqualTo("200")
    }

    @Test
    fun `closure`() {
        // given
        val env = GlobalEnvironment()
        compile("(define x 1)").run(env)
        compile("""
         (define range
          (lambda (x)
            (lambda ()
              (set! x (+ x 1))
              x)))     
        """.trimIndent()).run(env)
        compile("(define my-range (range 10))").run(env)

        // when / then
        assertThat(compile("(my-range)").run(env).let(::print)).isEqualTo("11")
        assertThat(compile("(my-range)").run(env).let(::print)).isEqualTo("12")
        assertThat(compile("(my-range)").run(env).let(::print)).isEqualTo("13")
        assertThat(compile("x").run(env).let(::print)).isEqualTo("1")
    }

}
