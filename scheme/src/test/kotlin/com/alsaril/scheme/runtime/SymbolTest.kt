package com.alsaril.scheme.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SymbolTest {
    @Test
    fun `interns one symbol per name`() {
        assertThat(Symbol.of("x")).isSameAs(Symbol.of("x"))
        assertThat(Symbol.of("x")).isNotSameAs(Symbol.of("y"))
        assertThat(Symbol.of("x").name).isEqualTo("x")
    }
}
