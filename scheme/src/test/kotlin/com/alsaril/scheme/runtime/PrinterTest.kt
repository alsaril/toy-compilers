package com.alsaril.scheme.runtime

import com.alsaril.scheme.runtime.Printer.print
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class PrinterTest {
    @ParameterizedTest
    @MethodSource("values")
    fun `prints every kind of value`(value: Any, expected: String) {
        assertThat(print(value)).isEqualTo(expected)
    }

    companion object {
        private val procedure = object : Function {
            override fun call(args: Any) = args
        }

        private fun list(vararg items: Any, tail: Any = Nil): Any = items.foldRight(tail) { item, rest -> Cons(item, rest) }

        @JvmStatic
        fun values() = listOf(
            arguments(true, "#t"),
            arguments(false, "#f"),
            arguments(-5, "-5"),
            arguments(Symbol.of("x"), "x"),
            arguments(Nil, "()"),
            arguments(list(1, 2, 3), "(1 2 3)"),
            arguments(list(Nil, Nil), "(() ())"),
            arguments(list(1, tail = 2), "(1 . 2)"),
            arguments(list(1, list(2, list(3)), tail = 4), "(1 (2 (3)) . 4)"),
            arguments(Unspecified, "#<unspecified>"),
            arguments(procedure, "#<procedure>"),
            arguments(list(procedure, procedure), "(#<procedure> #<procedure>)"),
        )
    }
}
