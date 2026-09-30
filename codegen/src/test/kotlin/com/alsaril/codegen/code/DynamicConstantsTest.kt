package com.alsaril.codegen.code

import com.alsaril.codegen.ByteClassLoader
import com.alsaril.codegen.classfile.AccessFlag.PRIVATE
import com.alsaril.codegen.classfile.AccessFlag.PUBLIC
import com.alsaril.codegen.classfile.AccessFlag.STATIC
import com.alsaril.codegen.classfile.attributes.BootstrapMethod
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.constantpool.*
import com.alsaril.codegen.constantpool.ConstantMethodHandleInfo.ReferenceKind.INVOKE_STATIC
import com.alsaril.codegen.instruction.*
import com.alsaril.codegen.verification.PrimitiveType.INTEGER
import com.alsaril.codegen.verification.PrimitiveType.LONG
import com.alsaril.codegen.verification.ReferenceType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DynamicConstantsTest {

    /** the descriptor of a bootstrap method taking [args] after the lookup, name and type, and returning [returns] */
    private fun bootstrap(returns: String, args: String = "") = "($CBP$args)$returns"

    @Nested
    inner class Pool {

        @Test
        fun `registers the bootstrap handle, then the constant typed as the bootstrap method returns`() {
            // given
            val cp = UpdatableConstantPool()
            val builder = builder(cp)
            val owner = builder.clazz("B")

            // when
            val pointer = builder.constantDynamic(owner, "boot", bootstrap("I"))

            // then the method ref and a static handle to it, and the constant behind its name-and-type
            assertThat(pointer).isEqualTo(DataPointer(11, INTEGER))
            assertThat(cp.build().entries).containsExactly(
                ConstantUtf8Info("B"),
                ConstantClassInfo(nameIndex = 1),
                ConstantUtf8Info("boot"),
                ConstantUtf8Info(bootstrap("I")),
                ConstantNameAndTypeInfo(nameIndex = 3, descriptorIndex = 4),
                ConstantMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                ConstantMethodHandleInfo(INVOKE_STATIC, referenceIndex = 6),
                ConstantUtf8Info("_"),
                ConstantUtf8Info("I"),
                ConstantNameAndTypeInfo(nameIndex = 8, descriptorIndex = 9),
                ConstantDynamicInfo(bootstrapMethodIndex = 0, nameAndTypeIndex = 10),
            )
            assertThat(builder.bootstrapMethods.methods()).containsExactly(BootstrapMethod(7, emptyList()))
        }

        @Test
        fun `types the constant as the class the bootstrap method returns`() {
            // given
            val builder = builder()

            // when
            val pointer = builder.constantDynamic(builder.clazz("B"), "boot", bootstrap("Ljava/lang/String;"))

            // then
            assertThat(pointer.type).isEqualTo(ReferenceType("java/lang/String"))
        }

        @Test
        fun `types a long constant as two slots, for ldc2_w to load`() {
            // given
            val builder = builder()

            // when
            val pointer = builder.constantDynamic(builder.clazz("B"), "boot", bootstrap("J"))

            // then
            assertThat(pointer.type).isEqualTo(LONG)
            assertThat(ldc2_w(pointer).stackEffect()).isEqualTo(gives(LONG))
        }

        @Test
        fun `passes the arguments to the bootstrap method as their pool indices`() {
            // given
            val builder = builder()
            val first = builder.int(1)
            val second = builder.string("s")

            // when
            builder.constantDynamic(builder.clazz("B"), "boot", bootstrap("I", "ILjava/lang/String;"), first, second)

            // then
            assertThat(builder.bootstrapMethods.methods().single().bootstrapArguments)
                .containsExactly(first.index, second.index)
        }

        @Test
        fun `reuses one constant and one bootstrap method for a repeated constant`() {
            // given
            val builder = builder()
            val owner = builder.clazz("B")

            // when
            val first = builder.constantDynamic(owner, "boot", bootstrap("I", "I"), builder.int(1))
            val second = builder.constantDynamic(owner, "boot", bootstrap("I", "I"), builder.int(1))

            // then
            assertThat(second).isEqualTo(first)
            assertThat(builder.bootstrapMethods.methods()).hasSize(1)
        }

        @Test
        fun `keeps constants with different arguments apart`() {
            // given
            val builder = builder()
            val owner = builder.clazz("B")

            // when
            val one = builder.constantDynamic(owner, "boot", bootstrap("I", "I"), builder.int(1))
            val two = builder.constantDynamic(owner, "boot", bootstrap("I", "I"), builder.int(2))

            // then
            assertThat(two.index).isNotEqualTo(one.index)
            assertThat(builder.bootstrapMethods.methods()).hasSize(2)
        }

        @Test
        fun `shares the bootstrap methods of every code builder of a class`() {
            // given
            val clazz = classFile("Shared", "java/lang/Object")
            val first = clazz.newCodeBuilder()
            val second = clazz.newCodeBuilder()

            // when
            val one = first.constantDynamic(first.self(), "boot", bootstrap("I"))
            val other = second.constantDynamic(second.self(), "boot", bootstrap("I"))

            // then
            assertThat(other).isEqualTo(one)
            assertThat(second.bootstrapMethods).isSameAs(first.bootstrapMethods)
        }
    }

    @Nested
    inner class Refuses {

        @Test
        fun `a bootstrap method that returns nothing`() {
            assertThatIllegalArgumentException()
                .isThrownBy { builder().run { constantDynamic(self(), "boot", bootstrap("V")) } }
                .withMessage("boot${bootstrap("V")} returns void, so it has no constant to give")
        }

        @Test
        fun `a method that does not take a lookup, a name and a type first`() {
            assertThatIllegalArgumentException()
                .isThrownBy { builder().run { constantDynamic(self(), "boot", "(Ljava/lang/String;)I") } }
                .withMessage("boot(Ljava/lang/String;)I cannot bootstrap a dynamic constant, which takes a lookup, a name and a type first")
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

        @Test
        fun `hands back what its bootstrap method returns`() {
            val result = call(
                "()Ljava/lang/Object;",
                { withBootstrap("boot", bootstrap("Ljava/lang/Object;")) { +ldc(string("Hello, world!")); +areturn } },
            ) {
                +ldc(constantDynamic(self(), "boot", bootstrap("Ljava/lang/Object;")))
                +areturn
            }

            assertThat(result).isEqualTo("Hello, world!")
        }

        @Test
        fun `hands its arguments to the bootstrap method after the lookup, the name and the type`() {
            // given a bootstrap method formatting its two arguments, which sit in slots 3 and 4
            val descriptor = bootstrap("Ljava/lang/Object;", "Ljava/lang/String;Ljava/lang/String;")

            val result = call(
                "()Ljava/lang/Object;",
                {
                    withBootstrap("boot", descriptor) {
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
                +ldc(constantDynamic(self(), "boot", descriptor, string("plus"), string("minus")))
                +areturn
            }

            assertThat(result).isEqualTo("Hello, world! plus minus")
        }

        @Test
        fun `takes another dynamic constant as an argument`() {
            // given an int constant handed on as the argument of a second one
            val result = call(
                "()Ljava/lang/Object;",
                {
                    withBootstrap("offset", bootstrap("I")) { +iconst(5); +ireturn }
                        .withBootstrap("tail", bootstrap("Ljava/lang/Object;", "I")) {
                            +ldc(string("Hello, world!"))
                            +iload(3)
                            invokevirtual(clazz("java/lang/String"), "substring", "(I)Ljava/lang/String;")
                            +areturn
                        }
                },
            ) {
                val offset = constantDynamic(self(), "offset", bootstrap("I"))
                +ldc(constantDynamic(self(), "tail", bootstrap("Ljava/lang/Object;", "I"), offset))
                +areturn
            }

            assertThat(result).isEqualTo(", world!")
        }

        @Test
        fun `loads a constant as the primitive its bootstrap method returns`() {
            // given the constant handed straight to ireturn, which takes nothing but an int
            val result = call("()I", { withBootstrap("boot", bootstrap("I")) { +iconst(5); +ireturn } }) {
                +ldc(constantDynamic(self(), "boot", bootstrap("I")))
                +ireturn
            }

            assertThat(result).isEqualTo(5)
        }

        @Test
        fun `loads a constant as the class its bootstrap method returns`() {
            // given the constant used as a String, which the verifier checks against its declared type
            val result = call(
                "()I",
                { withBootstrap("boot", bootstrap("Ljava/lang/String;")) { +ldc(string("abc")); +areturn } },
            ) {
                +ldc(constantDynamic(self(), "boot", bootstrap("Ljava/lang/String;")))
                invokevirtual(clazz("java/lang/String"), "length", "()I")
                +ireturn
            }

            assertThat(result).isEqualTo(3)
        }

        @Test
        fun `resolves a constant once, however often it is loaded`() {
            // given a bootstrap method handing out a new object on every call
            val result = call(
                "()I",
                {
                    withBootstrap("boot", bootstrap("Ljava/lang/Object;")) {
                        constructDefault(clazz("java/lang/Object"))
                        +areturn
                    }
                },
            ) {
                val constant = constantDynamic(self(), "boot", bootstrap("Ljava/lang/Object;"))
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
