package com.alsaril.scheme.compiler

import com.alsaril.scheme.assertNameError
import com.alsaril.scheme.assertRuntimeError
import com.alsaril.scheme.assertSyntaxError
import com.alsaril.scheme.execute
import com.alsaril.scheme.runtime.GlobalEnvironment
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
        "((lambda () 'a)), a",
        quoteCharacter = '$'
    )
    fun `calls a lambda`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @Test
    fun `evaluates to a procedure`() {
        assertThat(execute("(lambda (x) x)")).isEqualTo("#<procedure>")
    }

    @ParameterizedTest
    @CsvSource(
        "((lambda args args) 1 2 3), (1 2 3)",
        "((lambda args args)), ()",
        "((lambda (a . rest) rest) 1 2 3), (2 3)",
        "((lambda (a . rest) rest) 1), ()",
        quoteCharacter = '$'
    )
    fun `collects extra arguments into the rest parameter`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(define (inc x) (+ x 1)), (inc -1), 0",
        "(define (add x y) (+ x y 1)), (add -10 10), 1",
        "(define (zero) 0), (zero), 0",
        "(define (f . xs) xs), (f 1 2), (1 2)",
        "(define (g a . xs) xs), (g 1 2 3), (2 3)",
        "(define (test x) (set! x (* x 2)) (+ 1 x)), (test 20), 41",
        "(define (add+one x) (+ x 1)), (add+one 1), 2",
        quoteCharacter = '$'
    )
    fun `define with a parameter list defines a procedure`(definition: String, input: String, expected: String) {
        // given
        val env = GlobalEnvironment()
        execute(definition, env)

        // when / then
        assertThat(execute(input, env)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        // swapping f and g would give 7
        "((lambda (f g) (f (g 3))) (lambda (x) (* x 2)) (lambda (x) (+ x 1))), 8",
        "(list ((lambda () 1)) ((lambda () 2)) ((lambda () 3))), (1 2 3)",
        "(((lambda (a) (lambda (b) (- a b))) 10) 3), 7",
        "((((lambda (a) (lambda (b) (lambda (c) (list a b c)))) 1) 2) 3), (1 2 3)",
        quoteCharacter = '$'
    )
    fun `runs every lambda on a line as its own`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @Test
    fun `keeps lambdas of different lines apart`() {
        // given each line's first lambda, numbered the same in classes of its own
        val env = GlobalEnvironment()
        execute("(define (first) 'one)", env)
        execute("(define (second) 'two)", env)

        // when / then
        assertThat(execute("(list (first) (second) ((lambda () 'three)))", env)).isEqualTo("(one two three)")
    }

    @Test
    fun `define inside a body stays local`() {
        // given
        val env = GlobalEnvironment()
        execute("(define (f) (define f 15) (+ f 17))", env)

        // when / then
        assertThat(execute("(f)", env)).isEqualTo("32")
        assertThat(execute("(f)", env)).isEqualTo("32")

        // when / then
        assertThat(execute("((lambda (x) (define y 1) (+ x y)) 5)", env)).isEqualTo("6")
        assertNameError("y", "y is not defined", env)
    }

    @Test
    fun `calls itself recursively`() {
        // given
        val env = GlobalEnvironment()
        execute("(define (fact n) (if (= n 0) 1 (* n (fact (- n 1)))))", env)
        execute("(define (slow-add x y) (if (= x 0) y (slow-add (- x 1) (+ y 1))))", env)

        // when / then
        assertThat(execute("(fact 10)", env)).isEqualTo("3628800")
        assertThat(execute("(slow-add 100 100)", env)).isEqualTo("200")
    }

    @Test
    fun `closure mutates its captured parameter`() {
        // given
        val env = GlobalEnvironment()
        execute("(define x 1)", env)
        execute("(define range (lambda (x) (lambda () (set! x (+ x 1)) x)))", env)
        execute("(define my-range (range 10))", env)

        // when / then
        assertThat(execute("(my-range)", env)).isEqualTo("11")
        assertThat(execute("(my-range)", env)).isEqualTo("12")
        assertThat(execute("x", env)).isEqualTo("1")
    }

    @Test
    fun `closures have separate environments`() {
        // given
        val env = GlobalEnvironment()
        execute(
            """
            (define (make-counter)
              (define n 0)
              (lambda () (set! n (+ n 1)) n))
            """.trimIndent(),
            env
        )
        execute("(define c1 (make-counter))", env)
        execute("(define c2 (make-counter))", env)

        // when / then
        assertThat(execute("(list (c1) (c1) (c2))", env)).isEqualTo("(1 2 1)")
    }

    @Test
    fun `resolves free variables where the lambda was defined`() {
        // given
        val env = GlobalEnvironment()
        execute("(define x 1)", env)
        execute("(define (f) x)", env)
        execute("(define (g x) (f))", env)

        // when / then
        assertThat(execute("(g 2)", env)).isEqualTo("1")
    }

    @ParameterizedTest
    @CsvSource(
        "(lambda) | lambda: expected parameters and a body in (lambda)",
        "(lambda x) | lambda: expected a body in (lambda x)",
        "(lambda (x)) | lambda: expected a body in (lambda (x))",
        "(lambda (x x) x) | lambda: parameter x is declared more than once in (lambda (x x) x)",
        "(lambda (a . a) a) | lambda: parameter a is declared more than once in (lambda (a . a) a)",
        "(lambda (1) 1) | lambda: expected a symbol as a parameter, got 1 in (lambda (1) 1)",
        "(lambda (a . 1) a) | lambda: expected a symbol as the rest parameter, got 1 in (lambda (a . 1) a)",
        "(lambda 5 x) | lambda: expected a parameter list, got 5 in (lambda 5 x)",
        "(lambda (if) 1) | lambda: expected a symbol as a parameter, got if in (lambda (if) 1)",
        "(lambda (a . #f) a) | lambda: expected a symbol as the rest parameter, got #f in (lambda (a . #f) a)",
        "(lambda or 1) | lambda: expected a parameter list, got or in (lambda or 1)",
        "(lambda (x) . x) | expected a proper list of operands in (lambda (x) . x)",
        "(define (f)) | define: expected a body in (define (f))",
        "(define (1 x) x) | define: expected a symbol as the procedure name, got 1 in (define (1 x) x)",
        "(define (if x) x) | define: expected a symbol as the procedure name, got if in (define (if x) x)",
        "(define (f x x) x) | define: parameter x is declared more than once in (define (f x x) x)",
        "(define (f) . 1) | expected a proper list of operands in (define (f) . 1)",
        "((lambda args args) . 1) | expected a proper list of operands in ((lambda args args) . 1)",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `rejects malformed lambdas`(input: String, message: String) {
        assertSyntaxError(input, message)
    }

    @ParameterizedTest
    @CsvSource(
        "((lambda (a b) a) 1) | procedure (a b): expected 2 arguments, got 1",
        "((lambda (a) a) 1 2) | procedure (a): expected 1 argument, got 2",
        "((lambda () 1) 2) | procedure (): expected 0 arguments, got 1",
        "((lambda (a . rest) rest)) | procedure (a . rest): expected at least 1 argument, got 0",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `fails on wrong argument count`(input: String, message: String) {
        assertRuntimeError(input, message)
    }
}
