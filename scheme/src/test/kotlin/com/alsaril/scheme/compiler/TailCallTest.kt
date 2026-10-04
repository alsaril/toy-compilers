package com.alsaril.scheme.compiler

import com.alsaril.scheme.execute
import com.alsaril.scheme.runtime.GlobalEnvironment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class TailCallTest {
    /** procedures whose bodies end in a tail call, so calling one returns a `Dispatch` */
    private fun withTailCalls() = GlobalEnvironment().also {
        execute("(define (no) (not 1))", it)
        execute("(define (v) (car '(v)))", it)
        execute("(define (id x) (car (list x)))", it)
    }

    @ParameterizedTest
    @CsvSource(
        "(define (loop n) (if (= n 0) 'done (loop (- n 1)))), (loop 100000), done",
        "(define (count n acc) (if (= n 0) acc (count (- n 1) (+ acc 2)))), (count 100000 0), 200000",
        "(define (ev n) (if (= n 0) #t (if (= n 1) #f (ev (- n 2))))), (ev 100001), #f",
        "(define (f n) (or (= n 0) (f (- n 1)))), (f 100000), #t",
        "(define (f n) (and (> n -1) (if (= n 0) 'done (f (- n 1))))), (f 100000), done",
        "(define (f n acc) (set! acc (+ acc 1)) (if (= n 0) acc (f (- n 1) acc))), (f 100000 0), 100001",
        "(define (swap n a b) (if (= n 0) (list a b) (swap (- n 1) b a))), (swap 100001 'x 'y), (y x)",
        "(define (f n . rest) (if (= n 0) rest (f (- n 1) n))), (f 100000), (1)",
        "(define (f n) (define m (* n 2)) (if (= n 0) m (f (- n 1)))), (f 100000), 0",
        "(define (f n acc) (if (= n 0) ((car acc)) (f (- n 1) (cons (lambda () n) acc)))), (f 3 '()), 1",
        "(define (ev n) (define (od m) (if (= m 0) #f (ev (- m 1)))) (if (= n 0) #t (od (- n 1)))), (ev 100001), #f",
        quoteCharacter = '$'
    )
    fun `tail calls run in constant stack`(definition: String, input: String, expected: String) {
        // given
        val env = GlobalEnvironment()
        execute(definition, env)

        // when / then
        assertThat(execute(input, env)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(if (no) 'yes 'no), no",
        "(and (no) 'x), #f",
        "(or (no) 'x), x",
        "(not (no)), #t",
        "(list (v) (v)), (v v)",
        "(cons (no) (v)), (#f . v)",
        "((id id) 5), 5",
        quoteCharacter = '$'
    )
    fun `a tail call's value is resolved wherever it is used`(input: String, expected: String) {
        // given
        val env = withTailCalls()

        // when / then
        assertThat(execute(input, env)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(define x (v)), (list x), (v)",
        "(set! x (no)), (list x), (#f)",
        quoteCharacter = '$'
    )
    fun `a tail call's value is resolved before it is bound`(binding: String, input: String, expected: String) {
        // given
        val env = withTailCalls()
        execute("(define x 0)", env)

        // when
        execute(binding, env)

        // then
        assertThat(execute(input, env)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(define (f n) (set! f (lambda (m) 'redefined)) (f n)), (f 5), redefined",
        "(define (count n) (if (= n 3) (set! count (lambda (m) 100))) (if (= n 0) 0 (count (- n 1)))), (count 5), 100",
        "(define (f n) (if (= n 0) 'original (f (- n 1)))), ((lambda (g) (set! f (lambda (n) 'replaced)) (g 5)) f), replaced",
        "(define (f f) (f 5)), (f (lambda (x) (* x 2))), 10",
        "(define (f n) (define f (lambda (x) 'inner)) (f n)), (f 1), inner",
        quoteCharacter = '$'
    )
    fun `tail calls use the current binding of the name`(definition: String, input: String, expected: String) {
        // given
        val env = GlobalEnvironment()
        execute(definition, env)

        // when / then
        assertThat(execute(input, env)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(define (fact n) (if (= n 0) 1 (* n (fact (- n 1))))), (fact 10), 3628800",
        "(define (f n) (if (= n 0) 0 (+ 1 (f (- n 1))))), (f 1000), 1000",
        "(define (f n) (if (> n 0) (f (- n 1))) n), (f 3), 3",
        "(define (f n) (if (= n 0) #f (if (f (- n 1)) 'yes 'no))), (f 2), yes",
        "(define (f n) (or (and (= n 0) 'base) (and (f (- n 1)) n))), (f 3), 3",
        quoteCharacter = '$'
    )
    fun `calls outside tail position return their value to the caller`(definition: String, input: String, expected: String) {
        // given
        val env = GlobalEnvironment()
        execute(definition, env)

        // when / then
        assertThat(execute(input, env)).isEqualTo(expected)
    }
}
