package com.alsaril.codegen.instruction

import com.alsaril.codegen.instruction.PrimitiveType.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The verification types the analyzer tracks for every value on the stack and in a local,
 * after JVMS 4.10.1.2: how many slots each one takes, and which ones stand for an object
 * that can be handed on.
 */
class TypesTest {

    @Test
    fun `take two slots for a long and a double`() {
        assertThat(LONG.slots).isEqualTo(2)
        assertThat(DOUBLE.slots).isEqualTo(2)
    }

    @Test
    fun `take no slot for void, which is not a value`() {
        assertThat(VOID.slots).isZero()
    }

    @Test
    fun `take one slot for everything else`() {
        assertThat(listOf(TOP, INTEGER, FLOAT, NULL, UNINITIALIZED_THIS, AnyReference))
            .allSatisfy { assertThat(it.slots).isOne() }
        // a reference is one slot whatever it points at, an array of longs included
        assertThat(ReferenceType("java/lang/String").slots).isOne()
        assertThat(ReferenceType("[J").slots).isOne()
        assertThat(Uninitialized(0).slots).isOne()
    }

    @Test
    fun `count an object or null as a reference that can be handed on`() {
        assertThat(ReferenceType("java/lang/String").isAssignableToReference).isTrue()
        assertThat(ReferenceType("[I").isAssignableToReference).isTrue()
        assertThat(NULL.isAssignableToReference).isTrue()
        assertThat(AnyReference.isAssignableToReference).isTrue()
    }

    @Test
    fun `do not count an object whose constructor has not run`() {
        // it may sit in a local or be initialised, but nothing else may take it
        assertThat(UNINITIALIZED_THIS.isAssignableToReference).isFalse()
        assertThat(Uninitialized(0).isAssignableToReference).isFalse()
    }

    @Test
    fun `do not count a number, top or void as a reference`() {
        assertThat(listOf(TOP, INTEGER, FLOAT, LONG, DOUBLE, VOID))
            .allSatisfy { assertThat(it.isAssignableToReference).isFalse() }
    }
}
