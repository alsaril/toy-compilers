package com.alsaril.codegen.code

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The hierarchy a class is built with unless a front end hands over its own. It knows no
 * classes, so it answers the way that keeps the analyzer out of the verifier's way. How the
 * analyzer uses a hierarchy is checked in FramesTest and AnalysisTest.
 */
class ClassHierarchyTest {

    @Test
    fun `accepts any class where another is declared, leaving the check to the verifier`() {
        assertThat(LenientHierarchy.isAssignable("java/lang/String", "java/lang/Object")).isTrue()
        assertThat(LenientHierarchy.isAssignable("java/lang/String", "java/lang/CharSequence")).isTrue()
        assertThat(LenientHierarchy.isAssignable("java/lang/Integer", "java/lang/String")).isTrue()
    }

    @Test
    fun `meets a class with itself as that class`() {
        assertThat(LenientHierarchy.commonSuperclass("java/lang/String", "java/lang/String"))
            .isEqualTo("java/lang/String")
        assertThat(LenientHierarchy.commonSuperclass("[I", "[I")).isEqualTo("[I")
    }

    @Test
    fun `meets two different classes as Object, which every reference is`() {
        assertThat(LenientHierarchy.commonSuperclass("java/lang/String", "java/lang/Integer"))
            .isEqualTo("java/lang/Object")
        assertThat(LenientHierarchy.commonSuperclass("[I", "[B")).isEqualTo("java/lang/Object")
    }

    @Test
    fun `is what a class is built with unless another is given`() {
        // given
        val known = object : ClassHierarchy {
            override fun isAssignable(from: String, to: String) = false
            override fun commonSuperclass(a: String, b: String) = a
        }

        // then
        assertThat(ClassFileBuilder.classFile("Lenient", "java/lang/Object").hierarchy)
            .isSameAs(LenientHierarchy)
        assertThat(ClassFileBuilder.classFile("Known", "java/lang/Object", known).hierarchy)
            .isSameAs(known)
    }
}
