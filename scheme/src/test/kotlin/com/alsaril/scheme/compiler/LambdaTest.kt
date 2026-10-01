package com.alsaril.scheme.compiler

import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Printer.print
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
    fun `simple`(input: String, expected: String) {
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
        compile(
            """
         (define range
          (lambda (x)
            (lambda ()
              (set! x (+ x 1))
              x)))     
        """.trimIndent()
        ).run(env)
        compile("(define my-range (range 10))").run(env)

        // when / then
        assertThat(compile("(my-range)").run(env).let(::print)).isEqualTo("11")
        assertThat(compile("(my-range)").run(env).let(::print)).isEqualTo("12")
        assertThat(compile("(my-range)").run(env).let(::print)).isEqualTo("13")
        assertThat(compile("x").run(env).let(::print)).isEqualTo("1")
    }

    @ParameterizedTest
    @CsvSource(
        "(define (inc x) (+ x 1)), (inc -1), 0",
        "(define (add x y) (+ x y 1)), (add -10 10), 1",
        "(define (zero) 0), (zero), 0",
        "(define (f . xs) xs), (f 1 2), (1 2)",
        "(define (g a . xs) xs), (g 1 2 3), (2 3)",
        quoteCharacter = '$'
    )
    fun `defines lambda sugar`(def: String, input: String, expected: String) {
        // given
        val env = GlobalEnvironment()
        compile(def).run(env)

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `local define`() {
        // given
        val env = GlobalEnvironment()
        compile("(define (f) (define f 15) (+ f 17))").run(env)

        // when / then
        assertThat(compile("(f)").run(env).let(::print)).isEqualTo("32")
        assertThat(compile("(f)").run(env).let(::print)).isEqualTo("32")

        // when / then
        assertThat(compile("((lambda (x) (define y 1) (+ x y)) 5)").run(env).let(::print)).isEqualTo("6")
        assertThatThrownBy { compile("y").run(env) }
    }

    @Test
    fun `recursion`() {
        val env = GlobalEnvironment()
        compile("(define (fact n) (if (= n 0) 1 (* n (fact (- n 1)))))").run(env)

        // when
        val result = compile("(fact 10)").run(env).let(::print)

        // then
        assertThat(result).isEqualTo("3628800")
    }

    @Test
    fun `closures`() {
        val env = GlobalEnvironment()
        compile(
            """
            (define (make-counter)
              (define n 0)
              (lambda () (set! n (+ n 1)) n))
        """.trimIndent()
        ).run(env)
        compile("(define c1 (make-counter))").run(env)
        compile("(define c2 (make-counter))").run(env)

        // when
        val result = compile("(list (c1) (c1) (c2))").run(env).let(::print)

        // then
        assertThat(result).isEqualTo("(1 2 1)")
    }

    @Test
    fun `lexical scope`() {
        val env = GlobalEnvironment()
        compile("(define x 1)").run(env)
        compile("(define (f) x)").run(env)
        compile("(define (g x) (f))").run(env)

        // when
        val result = compile("(g 2)  ").run(env).let(::print)

        // then
        assertThat(result).isEqualTo("1")
    }

    @ParameterizedTest
    @CsvSource(
        "((lambda args args) 1 2 3), (1 2 3)",
        "((lambda args args)), ()",
        "((lambda (a . rest) rest) 1 2 3), (2 3)",
        "((lambda (a . rest) rest) 1), ()",
        quoteCharacter = '$'
    )
    fun `varargs`(input: String, expected: String) {
        // given
        val env = GlobalEnvironment()

        // when
        val result = compile(input).run(env).let(::print)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(lambda)",
        "(lambda x)",
        "(lambda (x))",
        "((lambda (a b) a) 1)",
        "((lambda (a) a) 1 2)",
        "((lambda (a . rest) rest))",
        "(lambda (x x) x)",
        "(lambda (a . a) a)",
        quoteCharacter = '$'
    )
    fun `throws on if syntax errors`(input: String) {
        // given
        val env = GlobalEnvironment()

        // when / then
        assertThatThrownBy { compile(input).run(env) }
    }
}
