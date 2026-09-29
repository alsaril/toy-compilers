package com.alsaril.scheme.compiler

import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Printer.print
import org.assertj.core.api.Assertions.assertThat
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

}
