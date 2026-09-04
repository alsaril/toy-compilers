package com.alsaril.bf

import org.antlr.v4.runtime.CharStreams.fromString
import org.antlr.v4.runtime.CommonTokenStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GrammarTest {
    @Test
    fun `should correctly parse a bf expression`() {
        // given
        val expr = "+[->+<]123"

        // when
        val lexer = BFLexer(fromString(expr))
        val parser = BFParser(CommonTokenStream(lexer))
        val parsed = parser.expr().toStringTree(parser)

        // then
        assertThat(parsed).isEqualTo("(expr (content (value +)) (content (block [ (content (value -)) (content (value >)) (content (value +)) (content (value <)) ])) <EOF>)")
    }
}