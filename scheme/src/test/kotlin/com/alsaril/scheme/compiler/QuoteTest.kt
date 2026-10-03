package com.alsaril.scheme.compiler

import com.alsaril.scheme.assertSyntaxError
import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.execute
import com.alsaril.scheme.runtime.Cons
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Symbol
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class QuoteTest {
    @ParameterizedTest
    @CsvSource(
        "'101, 101",
        "'x, x",
        "(quote x), x",
        "'(), ()",
        "'(1), (1)",
        "'(1 2), (1 2)",
        "(quote (1 2)), (1 2)",
        "'(1 . 2), (1 . 2)",
        "(quote (-2 . 3)), (-2 . 3)",
        "'(1 2 . 3), (1 2 . 3)",
        "'(1 2 . ()), (1 2)",
        "'(1 . (2 . ())), (1 2)",
        "''x, (quote x)",
        "'(1+ 1abc a+b), (1+ 1abc a+b)",
        "'(and 1 2 'c '(f g)), (and 1 2 (quote c) (quote (f g)))",
        quoteCharacter = '$'
    )
    fun `quote returns its operand unevaluated`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "(list 'a '(b c) '5 '#t '() 'd), (a (b c) 5 #t () d)",
        "(list 'a ''b '(quote c) 'd), (a (quote b) (quote c) d)",
        "(list 'a ((lambda () (list 'b 'c))) 'd), (a (b c) d)",
        "((lambda (x) (list 'a x ((lambda () 'c)))) 'b), (a b c)",
        "(list (if #f 'a 'b) (if #t 'c 'd)), (b c)",
        quoteCharacter = '$'
    )
    fun `quote keeps every datum on a line apart`(input: String, expected: String) {
        assertThat(execute(input)).isEqualTo(expected)
    }

    @Test
    fun `quote returns the same datum every time it runs`() {
        // given
        val env = GlobalEnvironment()
        val program = compile("'(1 x)")

        // when / then
        assertThat(program.run(env)).isSameAs(program.run(env))
    }

    @Test
    fun `quote interns its symbols`() {
        // given
        val env = GlobalEnvironment()

        // when
        val list = compile("'(x if)").run(env) as Cons

        // then
        assertThat(compile("'x").run(env)).isSameAs(Symbol.of("x"))
        assertThat(list.first).isSameAs(Symbol.of("x"))
        assertThat((list.second as Cons).first).isSameAs(Symbol.of("if"))
    }

    @ParameterizedTest
    @CsvSource(
        "(quote) | quote: expected 1 operand, got 0 in (quote)",
        "(quote 1 2) | quote: expected 1 operand, got 2 in (quote 1 2)",
        "(quote . 1) | expected a proper list of operands in (quote . 1)",
        delimiter = '|',
        quoteCharacter = '$'
    )
    fun `rejects malformed quote`(input: String, message: String) {
        assertSyntaxError(input, message)
    }
}
