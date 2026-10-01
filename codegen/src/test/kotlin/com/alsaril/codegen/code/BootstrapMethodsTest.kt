package com.alsaril.codegen.code

import com.alsaril.codegen.classfile.attributes.BootstrapMethod
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.INVOKE_STATIC
import com.alsaril.codegen.constantpool.ConstantMethodTypeInfo
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
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

    @Nested
    inner class Helpers {

        private val callSite = "(${IDP})Ljava/lang/invoke/CallSite;"

        @Test
        fun `register a bootstrap method of a handle and its arguments, numbered from zero`() {
            // given
            val builder = builder()
            val handle = builder.methodHandle(INVOKE_STATIC, builder.clazz("B"), "boot", callSite)
            val first = builder.int(1)
            val second = builder.string("s")

            // when
            val index = builder.bootstrap(handle, first, second)

            // then
            assertThat(index).isEqualTo(BootstrapPointer(0))
            assertThat(builder.bootstrapMethods.methods())
                .containsExactly(BootstrapMethod(handle.index, listOf(first.index, second.index)))
        }

        @Test
        fun `reuse one bootstrap method for an equal handle and arguments, and number the next one on`() {
            // given
            val builder = builder()
            val handle = builder.methodHandle(INVOKE_STATIC, builder.clazz("B"), "boot", callSite)

            // when
            val first = builder.bootstrap(handle, builder.int(1))
            val again = builder.bootstrap(handle, builder.int(1))
            val other = builder.bootstrap(handle, builder.int(2))

            // then
            assertThat(listOf(first, again, other)).containsExactly(BootstrapPointer(0), BootstrapPointer(0), BootstrapPointer(1))
        }

        @Test
        fun `share one table between every code builder of a class`() {
            // given
            val clazz = ClassFileBuilder.classFile("Shared", "java/lang/Object")
            val first = clazz.newCodeBuilder()
            val second = clazz.newCodeBuilder()

            // when
            val one = first.bootstrap(first.methodHandle(INVOKE_STATIC, first.self(), "boot", callSite))
            val other = second.bootstrap(second.methodHandle(INVOKE_STATIC, second.self(), "boot", callSite))

            // then
            assertThat(other).isEqualTo(one)
            assertThat(second.bootstrapMethods).isSameAs(first.bootstrapMethods)
        }

        @Test
        fun `bootstrap a lambda through the metafactory, with the method types and the implementation as arguments`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)
            val implementation = builder.methodHandle(INVOKE_STATIC, builder.self(), "impl", "(II)I")

            // when
            val index = builder.lambdaBootstrap("(Ljava/lang/Object;)Ljava/lang/Object;", implementation, "(Ljava/lang/Integer;)Ljava/lang/Integer;")

            // then the same handle and method types a second registration gets, as the pool reuses them
            val metafactory = builder.methodHandle(
                INVOKE_STATIC,
                builder.clazz("java/lang/invoke/LambdaMetafactory"),
                "metafactory",
                "(${IDP}Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            )
            assertThat(index).isEqualTo(BootstrapPointer(0))
            assertThat(builder.bootstrapMethods.methods()).containsExactly(
                BootstrapMethod(
                    metafactory.index,
                    listOf(
                        builder.methodType("(Ljava/lang/Object;)Ljava/lang/Object;").index,
                        implementation.index,
                        builder.methodType("(Ljava/lang/Integer;)Ljava/lang/Integer;").index,
                    ),
                ),
            )
            assertThat(cp.build().entries.filterIsInstance<ConstantMethodTypeInfo>()).hasSize(2)
        }
    }
}
