package com.alsaril.codegen.code

import com.alsaril.codegen.ByteClassLoader
import com.alsaril.codegen.classfile.AccessFlag.PRIVATE
import com.alsaril.codegen.classfile.AccessFlag.PUBLIC
import com.alsaril.codegen.classfile.AccessFlag.STATIC
import com.alsaril.codegen.classfile.attributes.BootstrapMethod
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.constantpool.*
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.*
import com.alsaril.codegen.instruction.*
import com.alsaril.codegen.verification.PrimitiveType.*
import com.alsaril.codegen.verification.ReferenceType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** The constants ldc and ldc2_w load, which a bootstrap method can take as arguments too. */
class ConstantsTest {

    @Nested
    inner class Values {

        @Test
        fun `registers an integer constant`() {
            // given
            val cp = UpdatableConstantPool()

            // when
            val pointer = builder(cp).int(42)

            // then
            assertThat(pointer).isEqualTo(DataPointer(1, INTEGER))
            assertThat(cp.build().entries).containsExactly(ConstantIntegerInfo(42))
        }

        @Test
        fun `registers a float constant`() {
            // given
            val cp = UpdatableConstantPool()

            // when
            val pointer = builder(cp).float(1.5f)

            // then
            assertThat(pointer).isEqualTo(DataPointer(1, FLOAT))
            assertThat(cp.build().entries).containsExactly(ConstantFloatInfo(1.5f))
        }

        @Test
        fun `registers a long constant across two slots`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            val pointer = builder.long(1L shl 40)
            val next = builder.int(1)

            // then the entry after it starts two slots on
            assertThat(pointer).isEqualTo(DataPointer(1, LONG))
            assertThat(next.index).isEqualTo(3)
            assertThat(cp.build().entries).containsExactly(ConstantLongInfo(1L shl 40), ConstantIntegerInfo(1))
        }

        @Test
        fun `registers a double constant across two slots`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            val pointer = builder.double(2.5)
            val next = builder.int(1)

            // then
            assertThat(pointer).isEqualTo(DataPointer(1, DOUBLE))
            assertThat(next.index).isEqualTo(3)
            assertThat(cp.build().entries).containsExactly(ConstantDoubleInfo(2.5), ConstantIntegerInfo(1))
        }

        @Test
        fun `registers a method type behind the utf8 of its descriptor`() {
            // given
            val cp = UpdatableConstantPool()

            // when
            val pointer = builder(cp).methodType("(I)V")

            // then
            assertThat(pointer).isEqualTo(DataPointer(2, ReferenceType("java/lang/invoke/MethodType")))
            assertThat(cp.build().entries).containsExactly(
                ConstantUtf8Info("(I)V"),
                ConstantMethodTypeInfo(descriptorIndex = 1),
            )
        }

        @Test
        fun `reuses one pool entry for a repeated method type`() {
            // given
            val builder = builder()

            // then
            assertThat(builder.methodType("(I)V")).isEqualTo(builder.methodType("(I)V"))
            assertThat(builder.methodType("(J)V")).isNotEqualTo(builder.methodType("(I)V"))
        }

        @Test
        fun `registers a string constant behind its utf8`() {
            // given
            val cp = UpdatableConstantPool()

            // when
            val pointer = builder(cp).string("boom")

            // then the pointer addresses the string entry, not the utf8 it wraps
            assertThat(pointer).isEqualTo(DataPointer(2, ReferenceType("java/lang/String")))
            assertThat(cp.build().entries).containsExactly(
                ConstantUtf8Info("boom"),
                ConstantStringInfo(valueIndex = 1),
            )
        }

        @Test
        fun `reuses one pool entry for a repeated constant`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            val int = builder.int(1)
            val string = builder.string("s")

            // then
            assertThat(builder.int(1)).isEqualTo(int)
            assertThat(builder.string("s")).isEqualTo(string)
            assertThat(cp.build().entries).hasSize(3)
        }

        @Test
        fun `keeps an integer and a string apart`() {
            // given
            val builder = builder()

            // then
            assertThat(builder.int(1)).isNotEqualTo(builder.string("1"))
        }

        @Test
        fun `keeps an integer and a float of the same value apart`() {
            // given
            val builder = builder()

            // then
            assertThat(builder.int(1)).isNotEqualTo(builder.float(1.0f))
        }
    }

    @Nested
    inner class MethodHandles {

        private val handle = ReferenceType("java/lang/invoke/MethodHandle")

        @Test
        fun `register a ref and a handle of the given kind to it`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            val pointer = builder.methodHandle(INVOKE_STATIC, builder.clazz("A"), "f", "(I)V")

            // then
            assertThat(pointer).isEqualTo(DataPointer(7, handle))
            assertThat(cp.build().entries).containsExactly(
                ConstantUtf8Info("A"),
                ConstantClassInfo(nameIndex = 1),
                ConstantUtf8Info("f"),
                ConstantUtf8Info("(I)V"),
                ConstantNameAndTypeInfo(nameIndex = 3, descriptorIndex = 4),
                ConstantMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                ConstantMethodHandleInfo(INVOKE_STATIC, referenceIndex = 6),
            )
        }

        @Test
        fun `refer to a field for the field kinds`() {
            listOf(GET_FIELD, GET_STATIC, PUT_FIELD, PUT_STATIC).forEach { kind ->
                // given
                val cp = UpdatableConstantPool()
                val builder = builder(cp)

                // when
                builder.methodHandle(kind, builder.clazz("A"), "x", "I")

                // then
                assertThat(cp.build().entries.takeLast(2)).containsExactly(
                    ConstantFieldRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                    ConstantMethodHandleInfo(kind, referenceIndex = 6),
                )
            }
        }

        @Test
        fun `refer to a method for the method kinds`() {
            listOf(INVOKE_VIRTUAL, INVOKE_STATIC, INVOKE_SPECIAL).forEach { kind ->
                // given
                val cp = UpdatableConstantPool()
                val builder = builder(cp)

                // when
                builder.methodHandle(kind, builder.clazz("A"), "f", "()V")

                // then
                assertThat(cp.build().entries.takeLast(2)).containsExactly(
                    ConstantMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                    ConstantMethodHandleInfo(kind, referenceIndex = 6),
                )
            }
        }

        @Test
        fun `refer to an interface method for invokeinterface`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            builder.methodHandle(INVOKE_INTERFACE, builder.clazz("A"), "f", "()V")

            // then
            assertThat(cp.build().entries.takeLast(2)).containsExactly(
                ConstantInterfaceMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                ConstantMethodHandleInfo(INVOKE_INTERFACE, referenceIndex = 6),
            )
        }

        @Test
        fun `refer to an interface method for a static or special handle on an interface`() {
            listOf(INVOKE_STATIC, INVOKE_SPECIAL).forEach { kind ->
                // given
                val cp = UpdatableConstantPool()
                val builder = builder(cp)

                // when
                builder.methodHandle(kind, builder.clazz("A"), "f", "()V", onInterface = true)

                // then
                assertThat(cp.build().entries.takeLast(2)).containsExactly(
                    ConstantInterfaceMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                    ConstantMethodHandleInfo(kind, referenceIndex = 6),
                )
            }
        }

        @Test
        fun `keep handles to a method of a class and of an interface apart`() {
            // given
            val builder = builder()
            val owner = builder.clazz("A")

            // when
            val onClass = builder.methodHandle(INVOKE_STATIC, owner, "f", "()V")
            val onInterface = builder.methodHandle(INVOKE_STATIC, owner, "f", "()V", onInterface = true)

            // then
            assertThat(onInterface).isNotEqualTo(onClass)
        }

        @Test
        fun `refuse an interface for any kind but static, special and interface`() {
            listOf(GET_FIELD, GET_STATIC, PUT_FIELD, PUT_STATIC, INVOKE_VIRTUAL).forEach { kind ->
                assertThatIllegalArgumentException()
                    .isThrownBy { builder().run { methodHandle(kind, clazz("A"), "f", "()V", onInterface = true) } }
                    .withMessage("a $kind handle cannot refer to a method of an interface, only a static, a special or an interface one can")
            }
            assertThatIllegalArgumentException()
                .isThrownBy { builder().run { methodHandle(NEW_INVOKE_SPECIAL, clazz("A"), "<init>", "()V", onInterface = true) } }
                .withMessage("a NEW_INVOKE_SPECIAL handle cannot refer to a method of an interface, only a static, a special or an interface one can")
        }

        @Test
        fun `refer to an interface method for invokeinterface whether it is asked for or not`() {
            // given
            val builder = builder()
            val owner = builder.clazz("A")

            // then
            assertThat(builder.methodHandle(INVOKE_INTERFACE, owner, "f", "()V", onInterface = true))
                .isEqualTo(builder.methodHandle(INVOKE_INTERFACE, owner, "f", "()V"))
        }

        @Test
        fun `refer to the constructor for a new object`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)

            // when
            builder.methodHandle(NEW_INVOKE_SPECIAL, builder.clazz("A"), "<init>", "()V")

            // then
            assertThat(cp.build().entries.takeLast(2)).containsExactly(
                ConstantMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                ConstantMethodHandleInfo(NEW_INVOKE_SPECIAL, referenceIndex = 6),
            )
        }

        @Test
        fun `refuse a constructor for any kind but a new object`() {
            assertThatIllegalArgumentException()
                .isThrownBy { builder().run { methodHandle(INVOKE_SPECIAL, clazz("A"), "<init>", "()V") } }
                .withMessage("a INVOKE_SPECIAL handle to <init>: only NEW_INVOKE_SPECIAL refers to <init>, and it refers to nothing else")
        }

        @Test
        fun `refuse a new object from anything but a constructor`() {
            assertThatIllegalArgumentException()
                .isThrownBy { builder().run { methodHandle(NEW_INVOKE_SPECIAL, clazz("A"), "create", "()V") } }
                .withMessage("a NEW_INVOKE_SPECIAL handle to create: only NEW_INVOKE_SPECIAL refers to <init>, and it refers to nothing else")
        }

        @Test
        fun `reuse one pool entry for a repeated handle`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)
            val first = builder.methodHandle(INVOKE_STATIC, builder.clazz("A"), "f", "()V")

            // when
            val second = builder.methodHandle(INVOKE_STATIC, builder.clazz("A"), "f", "()V")

            // then
            assertThat(second).isEqualTo(first)
            assertThat(cp.build().entries).hasSize(7)
        }
    }

    @Nested
    inner class DynamicConstants {

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
                val (name, bytes) = classFile("GenDynamic", "java/lang/Object")
                    .method("f", descriptor, PUBLIC, STATIC, codeBuilder = body)
                    .bootstraps()
                    .build()
                return ByteClassLoader().loadClass(name, bytes).getDeclaredMethod("f").invoke(null)
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
                            invokestatic(clazz("java/lang/String"), "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")
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
                                invokevirtual(clazz("java/lang/String"), "substring", "(I)Ljava/lang/String;")
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
                    invokevirtual(clazz("java/lang/String"), "length", "()I")
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
}
