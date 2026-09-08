package com.alsaril.codegen

import com.alsaril.codegen.Compiler.pipeline
import com.alsaril.codegen.classfile.ClassFileBuilder
import com.alsaril.codegen.classfile.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.MethodAccessFlag.FINAL
import com.alsaril.codegen.classfile.MethodAccessFlag.PUBLIC
import com.alsaril.codegen.classfile.code.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** The interface the classes generated below are asked to implement. */
interface Counter {
    fun count(): Int
}

/**
 * The pipeline every frontend is built on: parse, generate, load, then hand back an
 * instance of the interface the frontend declares. The frontends here are stand-ins,
 * so a failure points at the pipeline rather than at any one language.
 */
class CompilerTest {

    private val counterIface = Counter::class.java.name.replace('.', '/')

    private fun ClassFileBuilder.withConstructor() = method(
        "<init>",
        "()V",
        maxStack = 1,
        maxLocals = 1,
        PUBLIC,
    ) {
        aload(0)
        invokespecial(method(parent(), "<init>", "()V"))
        `return`()
    }

    private fun ClassFileBuilder.withCount(value: Int) = method(
        "count",
        "()I",
        maxStack = 1,
        maxLocals = 1,
        PUBLIC,
        FINAL,
    ) {
        iconst(value)
        ireturn()
    }

    /** the backend a well behaved frontend would bring */
    private fun counter(value: Int, name: String = "GenCounter") = classFile(name, "java/lang/Object")
        .iface(counterIface)
        .withConstructor()
        .withCount(value)
        .build()

    /** counts the characters of the source, standing in for a real frontend */
    private fun parse(source: String) = source.length

    private fun compile(source: String) =
        pipeline(source, ::parse, { counter(it) }, Counter::class.java)

    @Nested
    inner class Runs {

        @Test
        fun `returns an instance of the interface the frontend asked for`() {
            assertThat(compile("abc")).isInstanceOf(Counter::class.java)
        }

        @Test
        fun `returns an instance whose behaviour came from the source`() {
            assertThat(compile("abc").count()).isEqualTo(3)
            assertThat(compile("").count()).isZero()
        }

        @Test
        fun `hands the source to the frontend untouched`() {
            // given
            var seen: String? = null

            // when
            pipeline("  a b  ", { seen = it; it.length }, { counter(it) }, Counter::class.java)

            // then
            assertThat(seen).isEqualTo("  a b  ")
        }

        @Test
        fun `hands what the frontend produced to the backend`() {
            // given
            var seen: Int? = null

            // when
            pipeline("abcd", ::parse, { seen = it; counter(it) }, Counter::class.java)

            // then
            assertThat(seen).isEqualTo(4)
        }

        @Test
        fun `loads the class under the name the backend chose`() {
            val program = pipeline(
                "",
                ::parse,
                { counter(it, name = "GenNamed") },
                Counter::class.java,
            )

            assertThat(program.javaClass.name).isEqualTo("GenNamed")
        }

        @Test
        fun `gives every call its own class, even for the same source`() {
            // a fresh loader per call, so the same name does not clash
            val first = compile("ab")
            val second = compile("ab")

            assertThat(first).isNotSameAs(second)
            assertThat(first.javaClass).isNotSameAs(second.javaClass)
        }

        @Test
        fun `works through an interface with no methods of its own`() {
            val program = pipeline(
                "",
                ::parse,
                {
                    classFile("GenRunnable", "java/lang/Object")
                        .iface("java/lang/Runnable")
                        .withConstructor()
                        .method("run", "()V", maxStack = 0, maxLocals = 1, PUBLIC) { `return`() }
                        .build()
                },
                Runnable::class.java,
            )

            assertThat(program).isInstanceOf(Runnable::class.java)
        }
    }

    @Nested
    inner class Rejects {

        @Test
        fun `a class that does not implement the declared interface`() {
            assertThatIllegalArgumentException()
                .isThrownBy {
                    pipeline(
                        "",
                        ::parse,
                        {
                            classFile("GenBare", "java/lang/Object")
                                .withConstructor()
                                .withCount(it)
                                .build()
                        },
                        Counter::class.java,
                    )
                }
                .withMessage("generated class GenBare does not implement ${Counter::class.java.name}")
        }

        @Test
        fun `a class that implements some other interface`() {
            assertThatIllegalArgumentException()
                .isThrownBy {
                    pipeline(
                        "",
                        ::parse,
                        {
                            classFile("GenOther", "java/lang/Object")
                                .iface("java/lang/Runnable")
                                .withConstructor()
                                .method("run", "()V", maxStack = 0, maxLocals = 1, PUBLIC) { `return`() }
                                .build()
                        },
                        Counter::class.java,
                    )
                }
                .withMessageContaining("GenOther")
        }

        @Test
        fun `a class with no no-argument constructor`() {
            assertThatExceptionOfType(NoSuchMethodException::class.java)
                .isThrownBy {
                    pipeline(
                        "",
                        ::parse,
                        {
                            classFile("GenNoCtor", "java/lang/Object")
                                .iface(counterIface)
                                .withCount(it)
                                .build()
                        },
                        Counter::class.java,
                    )
                }
        }
    }

    @Nested
    inner class Failures {

        @Test
        fun `let a frontend failure through`() {
            assertThatIllegalArgumentException()
                .isThrownBy {
                    pipeline<Counter, Int>(
                        "oops",
                        { throw IllegalArgumentException("bad source") },
                        { counter(it) },
                        Counter::class.java,
                    )
                }
                .withMessage("bad source")
        }

        @Test
        fun `keep the backend from running when the frontend fails`() {
            // given
            var generated = false

            // when
            assertThatIllegalArgumentException().isThrownBy {
                pipeline<Counter, Int>(
                    "oops",
                    { throw IllegalArgumentException("bad source") },
                    { generated = true; counter(it) },
                    Counter::class.java,
                )
            }

            // then
            assertThat(generated).isFalse()
        }

        @Test
        fun `let a backend failure through`() {
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy {
                    pipeline<Counter, Int>(
                        "",
                        ::parse,
                        { throw IllegalStateException("cannot generate") },
                        Counter::class.java,
                    )
                }
                .withMessage("cannot generate")
        }

        @Test
        fun `reject bytes the jvm will not load`() {
            assertThatExceptionOfType(ClassFormatError::class.java)
                .isThrownBy {
                    pipeline<Counter, Int>(
                        "",
                        ::parse,
                        { "GenBroken" to bytesOf(0xCA, 0xFE, 0xBA, 0xBE) },
                        Counter::class.java,
                    )
                }
        }
    }
}
