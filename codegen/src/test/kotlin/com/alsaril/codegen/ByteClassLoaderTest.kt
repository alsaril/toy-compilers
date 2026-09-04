package com.alsaril.codegen

import com.alsaril.codegen.ByteClassLoader.loadClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ByteClassLoaderTest {

    @Test
    fun `loads a class from byte array`() {
        // given
        val name = "Test"
        val bytes = ByteClassLoaderTest::class.java.getResourceAsStream("/D.class")!!.use { it.readAllBytes() }

        // when
        val clazz = loadClass("D", bytes)
        val result = clazz.getDeclaredMethod("f").invoke(null)

        // then
        assertThat(result).isEqualTo(5)
    }
}