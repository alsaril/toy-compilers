package com.alsaril.codegen.classfile

import com.alsaril.codegen.classfile.PrimitiveType.BOOLEAN
import com.alsaril.codegen.classfile.PrimitiveType.BYTE
import com.alsaril.codegen.classfile.PrimitiveType.CHAR
import com.alsaril.codegen.classfile.PrimitiveType.DOUBLE
import com.alsaril.codegen.classfile.PrimitiveType.FLOAT
import com.alsaril.codegen.classfile.PrimitiveType.INT
import com.alsaril.codegen.classfile.PrimitiveType.LONG
import com.alsaril.codegen.classfile.PrimitiveType.SHORT
import com.alsaril.codegen.classfile.PrimitiveType.VOID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DescriptorTest {

    @Nested
    inner class Primitives {

        @Test
        fun `map every letter to its type`() {
            assertThat(parseType("B")).isEqualTo(BYTE)
            assertThat(parseType("C")).isEqualTo(CHAR)
            assertThat(parseType("D")).isEqualTo(DOUBLE)
            assertThat(parseType("F")).isEqualTo(FLOAT)
            assertThat(parseType("I")).isEqualTo(INT)
            assertThat(parseType("J")).isEqualTo(LONG)
            assertThat(parseType("S")).isEqualTo(SHORT)
            assertThat(parseType("Z")).isEqualTo(BOOLEAN)
            assertThat(parseType("V")).isEqualTo(VOID)
        }

        @Test
        fun `take two slots for a long and a double`() {
            assertThat(LONG.slots).isEqualTo(2)
            assertThat(DOUBLE.slots).isEqualTo(2)
        }

        @Test
        fun `take no slot at all for void, which is not a value`() {
            assertThat(VOID.slots).isZero()
        }

        @Test
        fun `take one slot for everything narrower`() {
            assertThat(listOf(BYTE, CHAR, FLOAT, INT, SHORT, BOOLEAN))
                .allSatisfy { assertThat(it.slots).isOne() }
        }
    }

    @Nested
    inner class References {

        @Test
        fun `read the class name between the L and the semicolon`() {
            assertThat(parseType("Ljava/lang/String;"))
                .isEqualTo(ReferenceType("java/lang/String"))
        }

        @Test
        fun `take one slot, whatever class they name`() {
            assertThat(parseType("Ljava/util/Map;").slots).isOne()
        }
    }

    @Nested
    inner class Arrays {

        @Test
        fun `wrap the type they hold`() {
            assertThat(parseType("[I")).isEqualTo(ArrayType(INT))
            assertThat(parseType("[Ljava/lang/String;"))
                .isEqualTo(ArrayType(ReferenceType("java/lang/String")))
        }

        @Test
        fun `nest`() {
            assertThat(parseType("[[I")).isEqualTo(ArrayType(ArrayType(INT)))
        }

        @Test
        fun `take one slot even when they hold something two wide`() {
            // the slot holds the reference, not the elements
            assertThat(parseType("[J").slots).isOne()
            assertThat(parseType("[[D").slots).isOne()
        }
    }

    @Nested
    inner class Functions {

        @Test
        fun `read a method taking nothing and returning nothing`() {
            assertThat(parseFunctionDescriptor("()V"))
                .isEqualTo(FunctionDescriptor(emptyList(), VOID))
        }

        @Test
        fun `read the arguments in the order they were written`() {
            assertThat(parseFunctionDescriptor("(Ljava/io/InputStream;Ljava/io/OutputStream;II)V"))
                .isEqualTo(
                    FunctionDescriptor(
                        listOf(
                            ReferenceType("java/io/InputStream"),
                            ReferenceType("java/io/OutputStream"),
                            INT,
                            INT,
                        ),
                        VOID,
                    )
                )
        }

        @Test
        fun `read an array argument`() {
            assertThat(parseFunctionDescriptor("([II)V"))
                .isEqualTo(FunctionDescriptor(listOf(ArrayType(INT), INT), VOID))
        }

        @Test
        fun `read a reference return`() {
            assertThat(parseFunctionDescriptor("(Ljava/util/Map;)F").returnType).isEqualTo(FLOAT)
            assertThat(parseFunctionDescriptor("()Ljava/lang/String;").returnType)
                .isEqualTo(ReferenceType("java/lang/String"))
        }

        @Test
        fun `add up to the slots the arguments occupy`() {
            // which is what a method needs before its body asks for any more
            assertThat(parseFunctionDescriptor("()V").args.sumOf { it.slots }).isZero()
            assertThat(parseFunctionDescriptor("(II)V").args.sumOf { it.slots }).isEqualTo(2)
            assertThat(parseFunctionDescriptor("(JD)V").args.sumOf { it.slots }).isEqualTo(4)
            assertThat(parseFunctionDescriptor("(J[JI)V").args.sumOf { it.slots }).isEqualTo(4)
        }
    }

    /**
     * Every way a descriptor can be wrong leaves as one kind of failure naming the place
     * it went wrong, so a caller can report a position without knowing which rule broke.
     */
    @Nested
    inner class Rejects {

        @Test
        fun `a letter that is not a descriptor`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parseType("Q") }
                .withMessage("unknown symbol at 0: Q")
            assertThatIllegalArgumentException()
                .isThrownBy { parseFunctionDescriptor("(IQ)V") }
                .withMessage("unknown symbol at 2: Q")
        }

        @Test
        fun `a type with anything after it`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parseType("II") }
                .withMessage("unexpected symbol at 1: I")
            assertThatIllegalArgumentException()
                .isThrownBy { parseType("Ljava/lang/String;I") }
                .withMessage("unexpected symbol at 18: I")
        }

        @Test
        fun `a reference that is never closed`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parseType("Ljava/lang/String") }
                .withMessage("';' expected at 17")
        }

        @Test
        fun `a descriptor that stops before the type does`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parseType("") }
                .withMessage("type expected at 0")
            assertThatIllegalArgumentException()
                .isThrownBy { parseType("[") }
                .withMessage("type expected at 1")
        }

        @Test
        fun `a method descriptor that does not open with a bracket`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parseFunctionDescriptor("I)V") }
                .withMessage("'(' expected at 0")
            assertThatIllegalArgumentException()
                .isThrownBy { parseFunctionDescriptor("") }
                .withMessage("'(' expected at 0")
        }

        @Test
        fun `a method descriptor whose arguments are never closed`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parseFunctionDescriptor("(II") }
                .withMessage("')' expected at 3")
        }

        @Test
        fun `void where an argument should be`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parseFunctionDescriptor("(V)V") }
                .withMessage("void cannot be an argument at 1")
            assertThatIllegalArgumentException()
                .isThrownBy { parseFunctionDescriptor("(IV)V") }
                .withMessage("void cannot be an argument at 2")
        }

        @Test
        fun `a method descriptor with no return type`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parseFunctionDescriptor("()") }
                .withMessage("type expected at 2")
        }

        @Test
        fun `a method descriptor with anything after its return type`() {
            assertThatIllegalArgumentException()
                .isThrownBy { parseFunctionDescriptor("()VV") }
                .withMessage("unexpected symbol at 3: V")
        }
    }

    @Test
    fun `reports every malformed descriptor the same way`() {
        val malformed = listOf("", "[", "Q", "II", "L", "Ljava/lang/String", "Ljava/lang/String;I")
        malformed.forEach { source ->
            assertThatIllegalArgumentException()
                .describedAs("parsing %s as a type", source)
                .isThrownBy { parseType(source) }
                .withMessageMatching(".* at \\d+.*")
        }

        val malformedFunctions =
            listOf("", "I)V", "(II", "()", "()VV", "(Q)V", "(Ljava/lang/String)V", "()I)", "(V)V")
        malformedFunctions.forEach { source ->
            assertThatIllegalArgumentException()
                .describedAs("parsing %s as a method", source)
                .isThrownBy { parseFunctionDescriptor(source) }
                .withMessageMatching(".* at \\d+.*")
        }
    }
}
