package com.alsaril.codegen

import com.alsaril.codegen.classfile.AccessFlag.PUBLIC
import com.alsaril.codegen.classfile.AccessFlag.STATIC
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.code.clazz
import com.alsaril.codegen.code.invokestatic
import com.alsaril.codegen.instruction.iconst
import com.alsaril.codegen.instruction.ireturn
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

class ByteClassLoaderTest {

    private val bytes = ByteClassLoaderTest::class.java.getResourceAsStream("/D.class")!!.use { it.readAllBytes() }

    @Test
    fun `loads a class from byte array`() {
        // when
        val clazz = ByteClassLoader().loadClass("D", bytes)
        val result = clazz.getDeclaredMethod("f").invoke(null)

        // then
        assertThat(result).isEqualTo(5)
    }

    @Test
    fun `defines a dependency when it is asked for`() {
        // given a class the application loader cannot find, so only the dependency can answer
        val dep = classFile("GenDep", "java/lang/Object")
            .method("f", "()I", PUBLIC, STATIC) {
                +iconst(5)
                +ireturn
            }
            .build()
        val loader = ByteClassLoader(listOf(dep))

        // when
        val clazz = loader.loadClass("GenDep")

        // then
        assertThat(clazz.classLoader).isSameAs(loader)
        assertThat(clazz.getDeclaredMethod("f").invoke(null)).isEqualTo(5)
        assertThat(loader.loadClass("GenDep")).isSameAs(clazz)
    }

    @Test
    fun `takes the names of packaged classes in the internal form`() {
        // given a root that reaches a dependency in another package
        val dep = classFile("gen/deps/Dep", "java/lang/Object")
            .method("f", "()I", PUBLIC, STATIC) {
                +iconst(5)
                +ireturn
            }
            .build()
        val (name, bytes) = classFile("gen/root/Root", "java/lang/Object")
            .method("f", "()I", PUBLIC, STATIC) {
                invokestatic(clazz("gen/deps/Dep"), "f", "()I")
                +ireturn
            }
            .build()

        // when
        val root = ByteClassLoader(listOf(dep)).loadClass(name, bytes)

        // then
        assertThat(root.name).isEqualTo("gen.root.Root")
        assertThat(root.getDeclaredMethod("f").invoke(null)).isEqualTo(5)
    }

    @Test
    fun `does not know a class that is not among its dependencies`() {
        assertThatExceptionOfType(ClassNotFoundException::class.java)
            .isThrownBy { ByteClassLoader(listOf("D" to bytes)).loadClass("GenMissing") }
    }
}
