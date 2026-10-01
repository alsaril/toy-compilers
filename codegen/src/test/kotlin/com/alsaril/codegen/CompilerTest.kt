package com.alsaril.codegen

import com.alsaril.codegen.Compiler.pipeline
import com.alsaril.codegen.code.ClassFileBuilder
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.AccessFlag.FINAL
import com.alsaril.codegen.classfile.AccessFlag.PUBLIC
import com.alsaril.codegen.code.*
import com.alsaril.codegen.instruction.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.lang.invoke.MethodHandles

/** The interface the classes generated below are asked to implement. */
interface Counter {
    fun count(): Int
}

/**
 * The pipeline every frontend is built on: parse, generate, define the class through the
 * frontend's lookup, then hand back an instance of the interface the frontend declares.
 * The frontends here are stand-ins, so a failure points at the pipeline rather than at
 * any one language.
 */
class CompilerTest {

    private val counterIface = Counter::class.java.name.replace('.', '/')

    private val lookup = MethodHandles.lookup()

    /** a class name in the package of [lookup], the only one it can define a class in */
    private fun inPackage(name: String) = "com/alsaril/codegen/$name"

    private fun ClassFileBuilder.withConstructor() = method(
        "<init>",
        "()V",
        PUBLIC,
    ) {
        +aload(0)
        invokespecial(parent(), "<init>", "()V")
        +`return`
    }

    private fun ClassFileBuilder.withCount(value: Int) = method(
        "count",
        "()I",
        PUBLIC,
        FINAL,
    ) {
        +iconst(value)
        +ireturn
    }

    /** the backend a well behaved frontend would bring */
    private fun counter(value: Int, name: String = "GenCounter") = classFile(inPackage(name), "java/lang/Object")
        .iface(counterIface)
        .withConstructor()
        .withCount(value)
        .build()

    /** counts the characters of the source, standing in for a real frontend */
    private fun parse(source: String) = source.length

    private fun compile(source: String) =
        pipeline(source, ::parse, { counter(it) }, Counter::class.java, lookup)

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
            pipeline("  a b  ", { seen = it; it.length }, { counter(it) }, Counter::class.java, lookup)

            // then
            assertThat(seen).isEqualTo("  a b  ")
        }

        @Test
        fun `hands what the frontend produced to the backend`() {
            // given
            var seen: Int? = null

            // when
            pipeline("abcd", ::parse, { seen = it; counter(it) }, Counter::class.java, lookup)

            // then
            assertThat(seen).isEqualTo(4)
        }

        @Test
        fun `defines a hidden class under the name the backend chose`() {
            val program = pipeline(
                "",
                ::parse,
                { counter(it, name = "GenNamed") },
                Counter::class.java,
                lookup,
            )

            assertThat(program.javaClass.isHidden).isTrue()
            assertThat(program.javaClass.name).startsWith("com.alsaril.codegen.GenNamed/")
        }

        @Test
        fun `gives every call its own class, even for the same source`() {
            // a hidden class each time, so the same name does not clash
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
                    classFile(inPackage("GenRunnable"), "java/lang/Object")
                        .iface("java/lang/Runnable")
                        .withConstructor()
                        .method("run", "()V", PUBLIC) { +`return` }
                        .build()
                },
                Runnable::class.java,
                lookup,
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
                            classFile(inPackage("GenBare"), "java/lang/Object")
                                .withConstructor()
                                .withCount(it)
                                .build()
                        },
                        Counter::class.java,
                        lookup,
                    )
                }
                .withMessageStartingWith("generated class com.alsaril.codegen.GenBare/")
                .withMessageEndingWith(" does not implement ${Counter::class.java.name}")
        }

        @Test
        fun `a class that implements some other interface`() {
            assertThatIllegalArgumentException()
                .isThrownBy {
                    pipeline(
                        "",
                        ::parse,
                        {
                            classFile(inPackage("GenOther"), "java/lang/Object")
                                .iface("java/lang/Runnable")
                                .withConstructor()
                                .method("run", "()V", PUBLIC) { +`return` }
                                .build()
                        },
                        Counter::class.java,
                        lookup,
                    )
                }
                .withMessageContaining("GenOther")
        }

        @Test
        fun `a class outside the package of the lookup`() {
            assertThatIllegalArgumentException()
                .isThrownBy {
                    pipeline(
                        "",
                        ::parse,
                        { classFile("GenElsewhere", "java/lang/Object").iface(counterIface).withConstructor().withCount(it).build() },
                        Counter::class.java,
                        lookup,
                    )
                }
                .withMessageContaining("not in same package as lookup class")
        }

        @Test
        fun `a class with no no-argument constructor`() {
            assertThatExceptionOfType(NoSuchMethodException::class.java)
                .isThrownBy {
                    pipeline(
                        "",
                        ::parse,
                        {
                            classFile(inPackage("GenNoCtor"), "java/lang/Object")
                                .iface(counterIface)
                                .withCount(it)
                                .build()
                        },
                        Counter::class.java,
                        lookup,
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
                        lookup,
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
                    lookup,
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
                        lookup,
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
                        { bytesOf(0xCA, 0xFE, 0xBA, 0xBE) },
                        Counter::class.java,
                        lookup,
                    )
                }
        }
    }
}
