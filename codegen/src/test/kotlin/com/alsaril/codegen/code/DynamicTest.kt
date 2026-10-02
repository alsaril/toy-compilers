package com.alsaril.codegen.code

import com.alsaril.codegen.classfile.AccessFlag.PRIVATE
import com.alsaril.codegen.classfile.AccessFlag.PUBLIC
import com.alsaril.codegen.classfile.AccessFlag.STATIC
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.constantpool.*
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.INVOKE_STATIC
import com.alsaril.codegen.instruction.*
import com.alsaril.codegen.load
import com.alsaril.codegen.verification.PrimitiveType.*
import com.alsaril.codegen.verification.ReferenceType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The two forms a bootstrap method resolves: a dynamic constant `ldc` loads, and an
 * `invokedynamic` call site. For each, what it registers in the pool, and what it does once
 * its class is loaded and run.
 */
class DynamicTest {

    private val a = ReferenceType("A")

    private fun CodeBuilder.emitted() = build().instructions

    @Nested
    inner class ConstantDynamic {

        /** the descriptor of a dynamic constant's bootstrap method taking [args] after the lookup, name and type */
        private fun bootstrapDescriptor(returns: String, args: String = "") = "($CBP$args)$returns"

        @Nested
        inner class Pool {

            @Test
            fun `registers the constant behind its name-and-type, naming the bootstrap method it is given`() {
                // given
                val cp = UpdatableConstantPool()

                // when
                val pointer = builder(cp).constantDynamic("answer", "I", BootstrapPointer(3))

                // then
                assertThat(pointer).isEqualTo(DataPointer(4, INTEGER))
                assertThat(cp.build().entries).containsExactly(
                    ConstantUtf8Info("answer"),
                    ConstantUtf8Info("I"),
                    ConstantNameAndTypeInfo(nameIndex = 1, descriptorIndex = 2),
                    ConstantDynamicInfo(bootstrapMethodIndex = 3, nameAndTypeIndex = 3),
                )
            }

            @Test
            fun `types the constant as its descriptor says`() {
                // given
                val builder = builder()

                // then
                assertThat(builder.constantDynamic("_", "Ljava/lang/String;", BootstrapPointer(0)).type)
                    .isEqualTo(ReferenceType("java/lang/String"))
                assertThat(builder.constantDynamic("_", "[I", BootstrapPointer(0)).type).isEqualTo(ReferenceType("[I"))
            }

            @Test
            fun `types a long constant as two slots, for ldc2_w to load`() {
                // when
                val pointer = builder().constantDynamic("_", "J", BootstrapPointer(0))

                // then
                assertThat(pointer.type).isEqualTo(LONG)
                assertThat(ldc2_w(pointer).stackEffect()).isEqualTo(gives(LONG))
            }

            @Test
            fun `reuses one entry for a repeated constant`() {
                // given
                val builder = builder()

                // then
                assertThat(builder.constantDynamic("_", "I", BootstrapPointer(0)))
                    .isEqualTo(builder.constantDynamic("_", "I", BootstrapPointer(0)))
            }

            @Test
            fun `keeps constants apart by name, by type and by bootstrap method`() {
                // given
                val builder = builder()

                // when
                val constants = listOf(
                    builder.constantDynamic("a", "I", BootstrapPointer(0)),
                    builder.constantDynamic("b", "I", BootstrapPointer(0)),
                    builder.constantDynamic("a", "J", BootstrapPointer(0)),
                    builder.constantDynamic("a", "I", BootstrapPointer(1)),
                )

                // then
                assertThat(constants.map { it.index }).doesNotHaveDuplicates()
            }
        }

        @Nested
        inner class Refuses {

            @Test
            fun `a constant typed void`() {
                assertThatIllegalArgumentException()
                    .isThrownBy { builder().constantDynamic("_", "V", BootstrapPointer(0)) }
                    .withMessage("a dynamic constant of type V would hold nothing")
            }
        }

        @Nested
        inner class Runs {

            /** loads a class holding [bootstraps] and a static f() of [descriptor] built from [body], and calls f */
            private fun call(
                descriptor: String,
                bootstraps: ClassFileBuilder.() -> ClassFileBuilder,
                body: CodeBuilder.() -> Unit,
            ): Any? {
                val bytes = classFile("GenDynamic", "java/lang/Object")
                    .method("f", descriptor, PUBLIC, STATIC, codeBuilder = body)
                    .bootstraps()
                    .build()
                return load(bytes).getDeclaredMethod("f").invoke(null)
            }

            private fun ClassFileBuilder.withBootstrap(name: String, descriptor: String, body: CodeBuilder.() -> Unit) =
                method(name, descriptor, PRIVATE, STATIC, codeBuilder = body)

            /** the bootstrap method [name] of this class, handed [args] */
            private fun CodeBuilder.own(name: String, descriptor: String, vararg args: DataPointer) =
                bootstrap(methodHandle(INVOKE_STATIC, self(), name, descriptor), *args)

            private fun CodeBuilder.jdk(name: String, descriptor: String, vararg args: DataPointer) =
                bootstrap(methodHandle(INVOKE_STATIC, clazz("java/lang/invoke/ConstantBootstraps"), name, descriptor), *args)

            @Test
            fun `hands back what its bootstrap method returns`() {
                val boot = bootstrapDescriptor("Ljava/lang/Object;")

                val result = call("()Ljava/lang/Object;", { withBootstrap("boot", boot) { +ldc(string("Hello, world!")); +areturn } }) {
                    +ldc(constantDynamic("_", "Ljava/lang/Object;", own("boot", boot)))
                    +areturn
                }

                assertThat(result).isEqualTo("Hello, world!")
            }

            @Test
            fun `hands its arguments to the bootstrap method after the lookup, the name and the type`() {
                // given a bootstrap method formatting its two arguments, which sit in slots 3 and 4
                val boot = bootstrapDescriptor("Ljava/lang/Object;", "Ljava/lang/String;Ljava/lang/String;")

                val result = call(
                    "()Ljava/lang/Object;",
                    {
                        withBootstrap("boot", boot) {
                            +ldc(string("Hello, world! %s %s"))
                            +iconst(2)
                            +anewarray(clazz("java/lang/Object"))
                            +dup
                            +iconst(0)
                            +aload(3)
                            +aastore
                            +dup
                            +iconst(1)
                            +aload(4)
                            +aastore
                            +invokestatic(clazz("java/lang/String"), "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")
                            +areturn
                        }
                    },
                ) {
                    +ldc(constantDynamic("_", "Ljava/lang/Object;", own("boot", boot, string("plus"), string("minus"))))
                    +areturn
                }

                assertThat(result).isEqualTo("Hello, world! plus minus")
            }

            @Test
            fun `takes another dynamic constant as an argument`() {
                // given an int constant handed on as the argument of a second one
                val offset = bootstrapDescriptor("I")
                val tail = bootstrapDescriptor("Ljava/lang/Object;", "I")

                val result = call(
                    "()Ljava/lang/Object;",
                    {
                        withBootstrap("offset", offset) { +iconst(5); +ireturn }
                            .withBootstrap("tail", tail) {
                                +ldc(string("Hello, world!"))
                                +iload(3)
                                +invokevirtual(clazz("java/lang/String"), "substring", "(I)Ljava/lang/String;")
                                +areturn
                            }
                    },
                ) {
                    val five = constantDynamic("_", "I", own("offset", offset))
                    +ldc(constantDynamic("_", "Ljava/lang/Object;", own("tail", tail, five)))
                    +areturn
                }

                assertThat(result).isEqualTo(", world!")
            }

            @Test
            fun `loads a constant typed as a primitive`() {
                // given the constant handed straight to ireturn, which takes nothing but an int
                val boot = bootstrapDescriptor("I")

                val result = call("()I", { withBootstrap("boot", boot) { +iconst(5); +ireturn } }) {
                    +ldc(constantDynamic("_", "I", own("boot", boot)))
                    +ireturn
                }

                assertThat(result).isEqualTo(5)
            }

            @Test
            fun `loads a constant typed as a class`() {
                // given the constant used as a String, which the verifier checks against its declared type
                val boot = bootstrapDescriptor("Ljava/lang/Object;")

                val result = call("()I", { withBootstrap("boot", boot) { +ldc(string("abc")); +areturn } }) {
                    +ldc(constantDynamic("_", "Ljava/lang/String;", own("boot", boot)))
                    +invokevirtual(clazz("java/lang/String"), "length", "()I")
                    +ireturn
                }

                assertThat(result).isEqualTo(3)
            }

            @Test
            fun `hands the bootstrap method the name and the type it was given`() {
                // given the JDK's getStaticFinal, which reads the static field the constant is named
                // after, from the class of the constant's type, here Integer for int
                val result = call("()I", { this }) {
                    +ldc(constantDynamic("MAX_VALUE", "I", jdk("getStaticFinal", bootstrapDescriptor("Ljava/lang/Object;"))))
                    +ireturn
                }

                assertThat(result).isEqualTo(Int.MAX_VALUE)
            }

            @Test
            fun `takes a method handle as an argument`() {
                // given the JDK's invoke, which calls the handle on the remaining arguments
                val invoke = bootstrapDescriptor("Ljava/lang/Object;", "Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;")

                val result = call("()I", { this }) {
                    val max = methodHandle(INVOKE_STATIC, clazz("java/lang/Math"), "max", "(II)I")
                    +ldc(constantDynamic("_", "I", jdk("invoke", invoke, max, int(3), int(7))))
                    +ireturn
                }

                assertThat(result).isEqualTo(7)
            }

            @Test
            fun `resolves a constant once, however often it is loaded`() {
                // given a bootstrap method handing out a new object on every call
                val boot = bootstrapDescriptor("Ljava/lang/Object;")

                val result = call(
                    "()I",
                    { withBootstrap("boot", boot) { constructDefault(clazz("java/lang/Object")); +areturn } },
                ) {
                    val constant = constantDynamic("_", "Ljava/lang/Object;", own("boot", boot))
                    +ldc(constant)
                    +ldc(constant)
                    val same = +if_acmpeq
                    +iconst(0)
                    +ireturn
                    link(same, +iconst(1))
                    +ireturn
                }

                // then both loads give the one object the constant resolved to
                assertThat(result).isEqualTo(1)
            }
        }
    
    }

    @Nested
    inner class InvokeDynamic {

        @Nested
        inner class Pool {

            @Test
            fun `registers the call site behind its name-and-type, with no receiver among its operands`() {
                // given
                val cp = UpdatableConstantPool()
                val builder = builder(cp)

                // when
                val call = builder.invokedynamic("make", "(IJ)LA;", BootstrapPointer(3))

                // then the arguments alone, and the result
                assertThat(call).isEqualTo(invokedynamic(4, listOf(INTEGER, LONG), a))
                assertThat(cp.build().entries).containsExactly(
                    ConstantUtf8Info("make"),
                    ConstantUtf8Info("(IJ)LA;"),
                    ConstantNameAndTypeInfo(nameIndex = 1, descriptorIndex = 2),
                    ConstantInvokeDynamicInfo(bootstrapMethodIndex = 3, nameAndTypeIndex = 3),
                )
            }

            @Test
            fun `emits one instruction per add, sharing the pool entry of an equal call site`() {
                // given
                val cp = UpdatableConstantPool()
                val builder = builder(cp)

                // when
                with(builder) {
                    +invokedynamic("make", "()V", BootstrapPointer(0))
                    +invokedynamic("make", "()V", BootstrapPointer(0))
                }

                // then the JVM links each instruction on its own, so the entry is all they share
                assertThat(builder.emitted()).containsExactly(invokedynamic(4, emptyList(), VOID), invokedynamic(4, emptyList(), VOID))
                assertThat(cp.build().entries).hasSize(4)
            }

            @Test
            fun `keeps call sites of different bootstrap methods apart`() {
                // given
                val builder = builder()

                // when
                val first = builder.invokedynamic("make", "()V", BootstrapPointer(0))
                val second = builder.invokedynamic("make", "()V", BootstrapPointer(1))

                // then
                assertThat(first.index).isNotEqualTo(second.index)
            }
        }

        @Nested
        inner class Runs {

            private fun call(argument: Int, members: ClassFileBuilder.() -> ClassFileBuilder): Any? {
                val bytes = classFile("GenIndy", parent = "java/lang/Object").members().build()
                return load(bytes).getDeclaredMethod("call", Int::class.java).invoke(null, argument)
            }

            @Test
            fun `links a call site to the handle its bootstrap method is given`() {
                // given a bootstrap method binding the site to the handle it takes, adapted to the type the site asks for
                val bind = "(${IDP}Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;"

                val result = call(4) {
                    method("call", "(I)Ljava/lang/Object;", PUBLIC, STATIC) {
                        +iload(0)
                        val impl = methodHandle(INVOKE_STATIC, self(), "impl", "(I)Ljava/lang/String;")
                        +invokedynamic("_", "(I)Ljava/lang/Object;", bootstrap(methodHandle(INVOKE_STATIC, self(), "bind", bind), impl))
                        +areturn
                    }
                    .method("bind", bind, PRIVATE, STATIC) {
                        +new(clazz("java/lang/invoke/ConstantCallSite"))
                        +dup
                        +aload(3)
                        +aload(2)
                        +invokevirtual(clazz("java/lang/invoke/MethodHandle"), "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;")
                        +invokespecial(clazz("java/lang/invoke/ConstantCallSite"), "<init>", "(Ljava/lang/invoke/MethodHandle;)V")
                        +areturn
                    }
                    .method("impl", "(I)Ljava/lang/String;", PRIVATE, STATIC) {
                        +ldc(string("Hello, world!"))
                        +iload(0)
                        +invokevirtual(clazz("java/lang/String"), "substring", "(I)Ljava/lang/String;")
                        +areturn
                    }
                }

                assertThat(result).isEqualTo("o, world!")
            }

            @Test
            fun `makes a lambda capturing an argument through the metafactory`() {
                // given a Function made from impl with 10 captured as its first argument
                val result = call(42) {
                    method("call", "(I)I", PUBLIC, STATIC) {
                        +ldc(int(10))
                        +invokedynamic(
                            "apply",
                            "(I)Ljava/util/function/Function;",
                            lambdaBootstrap(
                                "(Ljava/lang/Object;)Ljava/lang/Object;",
                                methodHandle(INVOKE_STATIC, self(), "impl", "(II)I"),
                                "(Ljava/lang/Integer;)Ljava/lang/Integer;",
                            ),
                        )
                        +iload(0)
                        +invokestatic(clazz("java/lang/Integer"), "valueOf", "(I)Ljava/lang/Integer;")
                        +invokeinterface(clazz("java/util/function/Function"), "apply", "(Ljava/lang/Object;)Ljava/lang/Object;")
                        +checkcast(clazz("java/lang/Integer"))
                        +invokevirtual(clazz("java/lang/Integer"), "intValue", "()I")
                        +ireturn
                    }
                    .method("impl", "(II)I", PRIVATE, STATIC) {
                        +iload(0)
                        +iload(1)
                        +iadd
                        +ireturn
                    }
                }

                assertThat(result).isEqualTo(52)
            }
        }
    }
}
