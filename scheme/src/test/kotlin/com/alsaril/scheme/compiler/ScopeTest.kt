package com.alsaril.scheme.compiler

import com.alsaril.scheme.execute
import com.alsaril.scheme.runtime.GlobalEnvironment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ScopeTest {
    @ParameterizedTest
    @CsvSource(
        "((lambda (x) (define get (lambda () x)) (set! x 2) (get)) 1), 2",
        "((lambda (n) (define (inc) (set! n (+ n 1))) (define (get) n) (inc) (inc) (get)) 0), 2",
        "((lambda () (define n 0) (define (inc) (set! n (+ n 1))) (inc) n)), 1",
        quoteCharacter = '$'
    )
    fun `shares a captured variable between its scope and its closures`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        // the middle lambda never names x, but has to carry it to the innermost one
        "((((lambda (x) (lambda () (lambda () x))) 7))), 7",
        "((lambda (x) ((lambda () ((lambda () (set! x 5))) x))) 1), 5",
        quoteCharacter = '$'
    )
    fun `captures a variable through a lambda that never names it`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "((lambda (a b) ((lambda () (cons b a)))) 1 2), (2 . 1)",
        "((lambda (a b c) ((lambda () c))) 1 2 3), 3",
        "((lambda (a) ((lambda (b) ((lambda () (list b a)))) 2)) 1), (2 1)",
        quoteCharacter = '$'
    )
    fun `captures variables in any order`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "((lambda () (define (sum n) (if (= n 0) 0 (+ n (sum (- n 1))))) (sum 10))), 55",
        "((lambda () (define (ev? n) (if (= n 0) #t (od? (- n 1)))) (define (od? n) (if (= n 0) #f (ev? (- n 1)))) (ev? 7))), #f",
        // list is the local defined after f, not the global procedure
        "((lambda () (define (f) list) (define list 5) (f))), 5",
        quoteCharacter = '$'
    )
    fun `scopes an internal define over the whole body`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "((lambda (x) ((lambda (x) x) 2)) 1), 2",
        "((lambda (x) ((lambda (y) x) 2)) 1), 1",
        "((lambda (+) (+ 1 2)) -), -1",
        "((lambda (car) car) 1), 1",
        quoteCharacter = '$'
    )
    fun `shadows an outer variable`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(+ 1 2 3 4 5 6), 21",
        "(list 1 2 3 4 5 6 7), (1 2 3 4 5 6 7)",
        "((lambda (a b c d e f) (list f e)) 1 2 3 4 5 6), (6 5)",
        quoteCharacter = '$'
    )
    fun `calls with more than five arguments`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @Test
    fun `resolves a global defined after the procedure that uses it`() {
        // given
        val env = GlobalEnvironment()
        execute("(define (f) (g))", env)
        execute("(define (g) 42)", env)

        // when / then
        assertThat(execute("(f)", env)).isEqualTo("42")
    }

    @Test
    fun `sees a global redefined after the procedure that uses it`() {
        // given
        val env = GlobalEnvironment()
        execute("(define x 1)", env)
        execute("(define (f) x)", env)
        execute("(define x 2)", env)

        // when / then
        assertThat(execute("(f)", env)).isEqualTo("2")
    }

    @Test
    fun `assigns a global from inside a procedure`() {
        // given
        val env = GlobalEnvironment()
        execute("(define x 1)", env)
        execute("(define (bump) (set! x (+ x 1)))", env)
        execute("(bump)", env)

        // when / then
        assertThat(execute("x", env)).isEqualTo("2")
    }
}
