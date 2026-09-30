package com.alsaril.codegen.code

import com.alsaril.codegen.classfile.attributes.BootstrapMethod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BootstrapMethodsTest {

    private val methods = BootstrapMethods()

    @Test
    fun `starts empty`() {
        assertThat(methods.isEmpty()).isTrue()
        assertThat(methods.methods()).isEmpty()
    }

    @Test
    fun `numbers methods from zero in the order they were added`() {
        // when
        val first = methods.add(BootstrapMethod(1, emptyList()))
        val second = methods.add(BootstrapMethod(2, listOf(3)))

        // then
        assertThat(listOf(first, second)).containsExactly(0, 1)
        assertThat(methods.isEmpty()).isFalse()
        assertThat(methods.methods()).containsExactly(BootstrapMethod(1, emptyList()), BootstrapMethod(2, listOf(3)))
    }

    @Test
    fun `returns the existing index for an equal method`() {
        // given
        val first = methods.add(BootstrapMethod(1, listOf(2, 3)))

        // when
        val second = methods.add(BootstrapMethod(1, listOf(2, 3)))

        // then
        assertThat(second).isEqualTo(first)
        assertThat(methods.methods()).hasSize(1)
    }

    @Test
    fun `keeps one handle with different arguments apart`() {
        // when
        val none = methods.add(BootstrapMethod(1, emptyList()))
        val some = methods.add(BootstrapMethod(1, listOf(2)))
        val two = methods.add(BootstrapMethod(1, listOf(2, 3)))
        val swapped = methods.add(BootstrapMethod(1, listOf(3, 2)))

        // then
        assertThat(listOf(none, some, two, swapped)).containsExactly(0, 1, 2, 3)
    }
}
