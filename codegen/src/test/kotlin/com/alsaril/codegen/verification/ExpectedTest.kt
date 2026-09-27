package com.alsaril.codegen.verification

import com.alsaril.codegen.verification.PrimitiveType.INTEGER
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * What an instruction accepts for an operand. Which values each one lets through is up to the
 * analyzer and its class hierarchy, and is checked in AnalysisTest and GeneratedClassTest;
 * here, how they read in the analyzer's messages.
 */
class ExpectedTest {

    @Test
    fun `name a single type as the type itself`() {
        assertThat(OfType(INTEGER).toString()).isEqualTo("INTEGER")
        assertThat(OfType(ReferenceType("java/lang/Object")).toString())
            .isEqualTo("ReferenceType(descriptor=java/lang/Object)")
    }

    @Test
    fun `name the alternatives by their descriptors`() {
        assertThat(OneOf(ReferenceType("[B"), ReferenceType("[Z")).toString()).isEqualTo("one of [B, [Z")
    }

    @Test
    fun `take the alternatives listed or as arguments alike`() {
        assertThat(OneOf(ReferenceType("[B"), ReferenceType("[Z")))
            .isEqualTo(OneOf(listOf(ReferenceType("[B"), ReferenceType("[Z"))))
    }
}
