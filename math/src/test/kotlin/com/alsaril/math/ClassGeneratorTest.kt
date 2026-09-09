package com.alsaril.math

import com.alsaril.codegen.ByteClassLoader.loadClass
import com.alsaril.math.BinaryKind.ADD
import com.alsaril.math.BinaryKind.DIV
import com.alsaril.math.BinaryKind.MUL
import com.alsaril.math.BinaryKind.SUB
import com.alsaril.math.ClassGenerator.generate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

class ClassGeneratorTest {

    /** records what the generated preamble asks the map for, and in which order */
    private class CountingMap(private val backing: Map<String, Float>) : Map<String, Float> by backing {
        val lookups = mutableListOf<String>()

        override fun get(key: String): Float? {
            lookups.add(key)
            return backing[key]
        }
    }

    private fun program(ast: Node): Program {
        val (name, bytes) = generate(ast)
        return loadClass(name, bytes).getDeclaredConstructor().newInstance() as Program
    }

    private fun eval(ast: Node, variables: Map<String, Float> = emptyMap()) =
        program(ast).eval(variables)

    @Nested
    inner class Constants {

        @Test
        fun `returns a lone value`() {
            assertThat(eval(Value(3.5f))).isEqualTo(3.5f)
            assertThat(eval(Value(-2.5f))).isEqualTo(-2.5f)
        }

        @Test
        fun `returns a value from either compilation path`() {
            // 0, 1 and 2 are compiled one way and everything else another
            assertThat(eval(Value(0.0f))).isEqualTo(0.0f)
            assertThat(eval(Value(1.0f))).isEqualTo(1.0f)
            assertThat(eval(Value(2.0f))).isEqualTo(2.0f)
            assertThat(eval(Value(3.0f))).isEqualTo(3.0f)
        }
    }

    @Nested
    inner class Variables {

        @Test
        fun `reads a variable out of the map`() {
            assertThat(eval(Var("x"), mapOf("x" to 7.0f))).isEqualTo(7.0f)
        }

        @Test
        fun `reads a name of several letters`() {
            assertThat(eval(Var("width"), mapOf("width" to 4.0f))).isEqualTo(4.0f)
        }

        @Test
        fun `picks the right entry out of a map holding several`() {
            val variables = mapOf("x" to 1.0f, "y" to 2.0f, "z" to 3.0f)

            assertThat(eval(Var("y"), variables)).isEqualTo(2.0f)
        }

        @Test
        fun `reads the same variable twice`() {
            assertThat(eval(Op(MUL, Var("x"), Var("x")), mapOf("x" to 3.0f))).isEqualTo(9.0f)
        }

        @Test
        fun `reads a variable given the value it is asked for each call`() {
            // the class is compiled once and evaluated against different maps
            val program = program(Op(ADD, Var("x"), Value(1.0f)))

            assertThat(program.eval(mapOf("x" to 1.0f))).isEqualTo(2.0f)
            assertThat(program.eval(mapOf("x" to 41.0f))).isEqualTo(42.0f)
        }

        @Test
        fun `names the variable the map does not hold`() {
            // the preamble tests each value before storing it, so the failure can say which
            assertThatExceptionOfType(NoSuchElementException::class.java)
                .isThrownBy { eval(Var("missing")) }
                .withMessage("missing")
        }

        @Test
        fun `names the first missing variable, in the order they appear`() {
            val ast = Op(ADD, Var("a"), Op(MUL, Var("b"), Var("c")))

            assertThatExceptionOfType(NoSuchElementException::class.java)
                .isThrownBy { eval(ast, mapOf("a" to 1.0f)) }
                .withMessage("b")
        }

        @Test
        fun `ignores entries the expression never names`() {
            assertThat(eval(Var("x"), mapOf("x" to 1.0f, "unused" to 2.0f))).isEqualTo(1.0f)
        }

        @Test
        fun `looks a variable up once however often the expression uses it`() {
            // given x used three times over
            val variables = CountingMap(mapOf("x" to 3.0f))

            // when
            val result = program(Op(ADD, Op(MUL, Var("x"), Var("x")), Var("x"))).eval(variables)

            // then the value was read into a slot once and reused from there
            assertThat(result).isEqualTo(12.0f)
            assertThat(variables.lookups).containsExactly("x")
        }

        @Test
        fun `looks each variable up in the order it appears`() {
            // given
            val variables = CountingMap(mapOf("a" to 1.0f, "b" to 1.0f, "c" to 1.0f))

            // when c is named first in the tree even though the map lists it last
            program(Op(ADD, Var("c"), Op(ADD, Var("a"), Var("b")))).eval(variables)

            // then
            assertThat(variables.lookups).containsExactly("c", "a", "b")
        }

        @Test
        fun `looks them up again on the next call`() {
            // given the slots are filled per call, not held between them
            val variables = CountingMap(mapOf("x" to 1.0f))
            val program = program(Var("x"))

            // when
            program.eval(variables)
            program.eval(variables)

            // then
            assertThat(variables.lookups).containsExactly("x", "x")
        }

        @Test
        fun `reads variables from slots past the compact opcodes`() {
            // given ten variables, so the later slots need the operand form of fstore
            val names = ('a'..'j').map { it.toString() }
            val ast = names.map { Var(it) as Node }.reduce { left, right -> Op(ADD, left, right) }

            // then
            assertThat(eval(ast, names.associateWith { 1.0f })).isEqualTo(10.0f)
        }
    }

    @Nested
    inner class Operators {

        @Test
        fun `applies each operator`() {
            assertThat(eval(Op(ADD, Value(5.0f), Value(3.0f)))).isEqualTo(8.0f)
            assertThat(eval(Op(SUB, Value(5.0f), Value(3.0f)))).isEqualTo(2.0f)
            assertThat(eval(Op(MUL, Value(5.0f), Value(3.0f)))).isEqualTo(15.0f)
            assertThat(eval(Op(DIV, Value(6.0f), Value(3.0f)))).isEqualTo(2.0f)
        }

        @Test
        fun `keeps the operands in order for the ones that care`() {
            assertThat(eval(Op(SUB, Value(1.0f), Value(4.0f)))).isEqualTo(-3.0f)
            assertThat(eval(Op(DIV, Value(1.0f), Value(4.0f)))).isEqualTo(0.25f)
        }

        @Test
        fun `evaluates a nested tree in the order it is shaped`() {
            // (1 + 2 * 3) - 4 / 2
            val ast = Op(
                SUB,
                Op(ADD, Value(1.0f), Op(MUL, Value(2.0f), Value(3.0f))),
                Op(DIV, Value(4.0f), Value(2.0f)),
            )

            assertThat(eval(ast)).isEqualTo(5.0f)
        }

        @Test
        fun `divides the way floats do rather than failing`() {
            assertThat(eval(Op(DIV, Value(1.0f), Value(0.0f)))).isEqualTo(Float.POSITIVE_INFINITY)
            assertThat(eval(Op(DIV, Value(-1.0f), Value(0.0f)))).isEqualTo(Float.NEGATIVE_INFINITY)
            assertThat(eval(Op(DIV, Value(0.0f), Value(0.0f)))).isNaN()
        }

        @Test
        fun `computes in float precision, not double`() {
            // 16777216 is 2^24, the point where a float can no longer hold every integer,
            // so adding one to it is a no-op in float and would not be in double
            assertThat(eval(Op(ADD, Value(16_777_216.0f), Value(1.0f))))
                .isEqualTo(16_777_216.0f)
        }
    }

    @Nested
    inner class StackDepth {

        @Test
        fun `covers a variable standing on its own`() {
            assertThat(eval(Var("x"), mapOf("x" to 7.0f))).isEqualTo(7.0f)
        }

        @Test
        fun `covers a variable on either side of an operator`() {
            assertThat(eval(Op(ADD, Var("x"), Value(1.0f)), mapOf("x" to 1.0f))).isEqualTo(2.0f)
            assertThat(eval(Op(ADD, Value(1.0f), Var("x")), mapOf("x" to 1.0f))).isEqualTo(2.0f)
        }

        @Test
        fun `covers two variables under one operator`() {
            assertThat(eval(Op(MUL, Var("x"), Var("y")), mapOf("x" to 2.0f, "y" to 3.0f)))
                .isEqualTo(6.0f)
        }

        @Test
        fun `covers variables sitting at different depths`() {
            // x * y + z * w keeps a partial result on the stack while reading z and w
            val ast = Op(
                ADD,
                Op(MUL, Var("x"), Var("y")),
                Op(MUL, Var("z"), Var("w")),
            )
            val variables = mapOf("x" to 2.0f, "y" to 3.0f, "z" to 4.0f, "w" to 5.0f)

            assertThat(eval(ast, variables)).isEqualTo(26.0f)
        }

        @Test
        fun `covers a variable at the bottom of a right leaning tree`() {
            // a + (b + (c + d)), so the last read happens three slots deep
            val ast = Op(ADD, Var("a"), Op(ADD, Var("b"), Op(ADD, Var("c"), Var("d"))))
            val variables = mapOf("a" to 1.0f, "b" to 2.0f, "c" to 3.0f, "d" to 4.0f)

            assertThat(eval(ast, variables)).isEqualTo(10.0f)
        }

        @Test
        fun `covers a tree that reaches deeper than the preamble does`() {
            // seven values nested to the right outgrow the four slots the preamble needs
            var ast: Node = Value(1.0f)
            repeat(6) { ast = Op(ADD, Value(1.0f), ast) }

            assertThat(eval(ast)).isEqualTo(7.0f)
        }

        @Test
        fun `covers a preamble deeper than the expression that follows it`() {
            // a lone variable leaves a one deep expression behind a four deep preamble
            assertThat(eval(Var("x"), mapOf("x" to 1.0f))).isEqualTo(1.0f)
        }

        @Test
        fun `asks for no more depth than a chain of operators reaches`() {
            // a left leaning chain never holds more than two values, however long it runs,
            // and asking for more than that is legal and so invisible from running it
            var ast: Node = Value(1.0f)
            repeat(49) { ast = Op(ADD, ast, Value(1.0f)) }

            // the preamble's own four is the floor, and 50 terms must not raise it
            assertThat(maxStacks(generate(ast).second)).containsExactly(1, 4)
        }
    }

    @Nested
    inner class Negation {

        @Test
        fun `negates a value`() {
            assertThat(eval(Neg(Value(3.0f)))).isEqualTo(-3.0f)
        }

        @Test
        fun `negates a variable`() {
            assertThat(eval(Neg(Var("x")), mapOf("x" to 2.0f))).isEqualTo(-2.0f)
        }

        @Test
        fun `negates what a subtree came to`() {
            assertThat(eval(Neg(Op(ADD, Value(1.0f), Value(2.0f))))).isEqualTo(-3.0f)
        }

        @Test
        fun `negates twice back to where it started`() {
            assertThat(eval(Neg(Neg(Value(3.0f))))).isEqualTo(3.0f)
        }

        @Test
        fun `carries the sign of a negated zero`() {
            // -0.0 and 0.0 compare equal, so the division is what tells them apart
            assertThat(eval(Op(DIV, Value(1.0f), Neg(Value(0.0f)))))
                .isEqualTo(Float.NEGATIVE_INFINITY)
            assertThat(eval(Op(DIV, Value(1.0f), Value(0.0f))))
                .isEqualTo(Float.POSITIVE_INFINITY)
        }

        @Test
        fun `leaves the stack where it found it`() {
            // negation replaces the value it is given, so the operand beside it is unaffected
            assertThat(eval(Op(SUB, Neg(Value(1.0f)), Neg(Value(4.0f))))).isEqualTo(3.0f)
        }
    }

    @Nested
    inner class Nesting {

        private fun leftLeaning(depth: Int): Node {
            var node: Node = Value(1.0f)
            repeat(depth - 1) { node = Op(ADD, node, Value(1.0f)) }
            return node
        }

        private fun rightLeaning(depth: Int): Node {
            var node: Node = Value(1.0f)
            repeat(depth - 1) { node = Op(ADD, Value(1.0f), node) }
            return node
        }

        @Test
        fun `compiles a long chain of operators`() {
            // left leaning, so the stack never holds more than two values
            assertThat(eval(leftLeaning(1_000))).isEqualTo(1_000.0f)
        }

        @Test
        fun `compiles a tree that grows the stack with its depth`() {
            // right leaning, so max_stack has to grow to the depth of the tree
            assertThat(eval(rightLeaning(1_000))).isEqualTo(1_000.0f)
        }
    }

    @Nested
    inner class Shape {

        @Test
        fun `names the generated class Impl`() {
            assertThat(generate(Value(1.0f)).first).isEqualTo("Impl")
        }

        @Test
        fun `implements Program and nothing else`() {
            assertThat(program(Value(1.0f)).javaClass.interfaces)
                .containsExactly(Program::class.java)
        }

        @Test
        fun `declares eval as a public final method returning a float`() {
            // the descriptor has to match Program.eval once its generics are erased
            val method = program(Value(1.0f)).javaClass.getDeclaredMethod("eval", Map::class.java)

            assertThat(method.returnType).isEqualTo(Float::class.javaPrimitiveType)
            assertThat(Modifier.isPublic(method.modifiers)).isTrue()
            assertThat(Modifier.isFinal(method.modifiers)).isTrue()
        }

        @Test
        fun `builds the same bytes for the same tree`() {
            assertThat(generate(Op(ADD, Var("x"), Value(2.0f))).second)
                .isEqualTo(generate(Op(ADD, Var("x"), Value(2.0f))).second)
        }
    }
}
