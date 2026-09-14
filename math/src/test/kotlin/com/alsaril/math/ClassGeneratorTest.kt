package com.alsaril.math

import com.alsaril.codegen.ByteClassLoader.loadClass
import com.alsaril.math.BinaryKind.*
import com.alsaril.math.generator.ClassGenerator.generate
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
        fun `names the first missing variable the accessor reaches`() {
            // the accessor reads in slot order, most used first — so that is the order a
            // missing one is noticed in, not the order the names appear in the expression.
            // a comes first in the tree, b is read first because it is read six times
            var ast: Node = Op(ADD, Var("a"), Var("b"))
            repeat(5) { ast = Op(ADD, ast, Var("b")) }

            assertThatExceptionOfType(NoSuchElementException::class.java)
                .isThrownBy { eval(ast) }
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
        fun `keeps equally used variables in the order they appear`() {
            // given three variables read once each, so nothing separates them by usage
            val variables = CountingMap(mapOf("a" to 1.0f, "b" to 1.0f, "c" to 1.0f))

            // when c is named first in the tree even though the map lists it last
            program(Op(ADD, Var("c"), Op(ADD, Var("a"), Var("b")))).eval(variables)

            // then the sort leaves the tie alone, which is what keeps generation repeatable
            assertThat(variables.lookups).containsExactly("c", "a", "b")
        }

        @Test
        fun `looks up a variable a negation stands in front of`() {
            // given b under two negations and a beside them, so collecting has to come
            // back out of the nesting to reach a
            val variables = CountingMap(mapOf("a" to 1.0f, "b" to 2.0f))

            // when
            val result = program(Op(SUB, Neg(Neg(Var("b"))), Var("a"))).eval(variables)

            // then neither the order nor the count changed for being wrapped
            assertThat(result).isEqualTo(1.0f)
            assertThat(variables.lookups).containsExactly("b", "a")
        }

        @Test
        fun `counts a variable once across a negation and a plain use`() {
            // given
            val variables = CountingMap(mapOf("x" to 2.0f))

            // when
            val result = program(Op(ADD, Var("x"), Neg(Var("x")))).eval(variables)

            // then
            assertThat(result).isEqualTo(0.0f)
            assertThat(variables.lookups).containsExactly("x")
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

        /** the deepest stack any of the generated body methods asks for */
        private fun bodyStack(ast: Node): Int {
            val bytes = generate(ast).second
            return methodNames(bytes).zip(maxStacks(bytes))
                .filter { (name, _) -> name.startsWith("f") }
                .maxOf { (_, stack) -> stack }
        }

        private fun rightLeaning(terms: Int): Node {
            var node: Node = Value(1.0f)
            repeat(terms - 1) { node = Op(ADD, Value(1.0f), node) }
            return node
        }

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
        fun `asks for no more depth than the body reaches`() {
            // a left leaning chain never holds more than two values, however long it runs
            var ast: Node = Value(1.0f)
            repeat(999) { ast = Op(ADD, ast, Value(1.0f)) }

            assertThat(bodyStack(ast)).isEqualTo(2)
        }

        @Test
        fun `asks for exactly the depth a right leaning tree reaches`() {
            // each left operand waits on the stack while the right side is worked out,
            // so this one genuinely needs a slot per term
            assertThat(bodyStack(rightLeaning(3))).isEqualTo(3)
            assertThat(bodyStack(rightLeaning(10))).isEqualTo(10)
            assertThat(bodyStack(rightLeaning(100))).isEqualTo(100)
        }

        @Test
        fun `keeps the depth the preamble needs as a floor`() {
            // reading a variable into its slot reaches two deep on its own
            assertThat(bodyStack(Value(1.0f))).isEqualTo(2)
            assertThat(bodyStack(Var("x"))).isEqualTo(2)
        }

        @Test
        fun `counts a negation as leaving the stack where it found it`() {
            assertThat(bodyStack(Neg(Neg(Neg(Value(1.0f)))))).isEqualTo(2)
            assertThat(bodyStack(Op(ADD, Value(1.0f), Neg(rightLeaning(5))))).isEqualTo(6)
        }

        @Test
        fun `deduces the depth of a body it outlined`() {
            // 4000 terms spill into a second method, which then holds most of the chain
            var ast: Node = Value(1.0f)
            repeat(3_999) { ast = Op(ADD, Value(1.0f), ast) }

            assertThat(eval(ast)).isEqualTo(4_000.0f)
        }

        @Test
        fun `deduces the depth when neither side spills alone but the two together do`() {
            // each half is under the budget on its own, so only their sum forces the split
            val ast = Op(ADD, rightLeaning(2_000), rightLeaning(2_000))

            assertThat(eval(ast)).isEqualTo(4_000.0f)
        }

        @Test
        fun `compiles a tree far deeper than the old flat declaration allowed`() {
            var ast: Node = Value(1.0f)
            repeat(1_999) { ast = Op(ADD, Value(1.0f), ast) }

            assertThat(eval(ast)).isEqualTo(2_000.0f)
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

        @Test
        fun `compiles a tree far deeper than a recursive walk managed`() {
            // generating recursively used to overflow the stack somewhere above 5000
            assertThat(eval(leftLeaning(20_000))).isEqualTo(20_000.0f)
        }

        @Test
        fun `compiles a negation nested far deeper than that`() {
            var ast: Node = Value(1.0f)
            repeat(20_000) { ast = Neg(ast) }

            // an even number of negations cancel out
            assertThat(eval(ast, mapOf("x" to 1.0f))).isEqualTo(1.0f)
        }
    }

    @Nested
    inner class Splitting {

        private fun chainOf(name: String, terms: Int): Node {
            var node: Node = Var(name)
            repeat(terms - 1) { node = Op(ADD, node, Var(name)) }
            return node
        }

        private fun chain(terms: Int): Node {
            var node: Node = Var("x")
            repeat(terms - 1) { node = Op(ADD, node, Var("y")) }
            return node
        }

        private val ones = mapOf("x" to 1.0f, "y" to 1.0f)

        @Test
        fun `keeps a body that fits in one method`() {
            assertThat(methodNames(generate(Op(MUL, Var("a"), Value(2.0f))).second))
                .containsExactly("<init>", "getFloat", "f0", "eval")
        }

        @Test
        fun `outlines a body that does not fit`() {
            val names = methodNames(generate(chain(5_000)).second)

            assertThat(names).startsWith("<init>", "getFloat").endsWith("eval")
            assertThat(names.filter { it.startsWith("f") }).hasSizeGreaterThan(1)
        }

        @Test
        fun `evaluates the same however many methods it took`() {
            assertThat(eval(chain(5_000), ones)).isEqualTo(5_000.0f)
        }

        @Test
        fun `reads the variables it needs in every method it outlined`() {
            // the outlined halves each read x and y for themselves, from the map passed on
            assertThat(eval(chain(5_000), mapOf("x" to 2.0f, "y" to 3.0f)))
                .isEqualTo(2.0f + 3.0f * 4_999)
        }

        @Test
        fun `still names a variable the map does not hold`() {
            assertThatExceptionOfType(NoSuchElementException::class.java)
                .isThrownBy { eval(chain(5_000), mapOf("x" to 1.0f)) }
                .withMessage("y")
        }

        /** the slots the body methods declare, entry method last */
        private fun bodySlots(ast: Node): List<Int> {
            val bytes = generate(ast).second
            return methodNames(bytes).zip(maxLocals(bytes))
                .filter { (name, _) -> name.startsWith("f") }
                .map { (_, slots) -> slots }
        }

        @Test
        fun `numbers each method's slots from the variables that method uses`() {
            // ten variables are known, but each outlined half reads only one of them, so
            // neither should reserve room for the other nine
            val names = ('a'..'j').map { it.toString() }
            var known: Node = Var(names.first())
            names.drop(1).forEach { known = Op(ADD, known, Var(it)) }
            val ast = Op(ADD, Op(ADD, known, chainOf("a", 3_000)), chainOf("j", 3_000))

            // one slot for the map, one for the single variable the method reads
            assertThat(bodySlots(ast)).contains(2)
        }

        @Test
        fun `leaves no gap when a method reads only a late variable`() {
            // b is the second name seen, yet in a method of its own it takes the first slot
            val known = Op(ADD, Var("a"), Var("b"))
            val ast = Op(ADD, known, chainOf("b", 5_000))

            assertThat(bodySlots(ast)).contains(2)
        }

        @Test
        fun `still evaluates correctly once the slots have been renumbered`() {
            val names = ('a'..'j').map { it.toString() }
            var ast: Node = Var(names.first())
            names.drop(1).forEach { ast = Op(ADD, ast, Var(it)) }
            repeat(20) { ast = Op(ADD, ast, Var("j")) }

            val values = names.withIndex().associate { (i, n) -> n to (i + 1).toFloat() }

            assertThat(eval(ast, values)).isEqualTo(values.values.sum() + 10.0f * 20)
        }

        @Test
        fun `gives the most used variable the first slot`() {
            // the accessor fills slots in order, so the lookup order is the slot order
            val variables = CountingMap(mapOf("a" to 1.0f, "b" to 1.0f, "c" to 1.0f))
            var ast: Node = Op(ADD, Op(ADD, Var("a"), Var("b")), Var("c"))
            repeat(5) { ast = Op(ADD, ast, Var("c")) }
            repeat(2) { ast = Op(ADD, ast, Var("b")) }

            program(ast).eval(variables)

            assertThat(variables.lookups).containsExactly("c", "b", "a")
        }

        private fun overVariables(count: Int, terms: Int): Pair<Node, List<String>> {
            val names = (0 until count).map { index ->
                var n = index
                val sb = StringBuilder()
                do { sb.append('a' + n % 26); n /= 26 } while (n > 0)
                sb.toString()
            }
            var ast: Node = Var(names.first())
            names.drop(1).forEach { ast = Op(ADD, ast, Var(it)) }
            repeat(terms) { ast = Op(ADD, ast, Var(names[it % names.size])) }
            return ast to names
        }

        @Test
        fun `keeps every method inside the length budget, prelude included`() {
            // 8000 is hotspot's threshold for compiling a method at all, so a method past
            // it would silently stay interpreted. The prelude that reads the variables is
            // part of the method, so it has to be part of the estimate that splits it
            val (ast, _) = overVariables(128, 6_000)

            assertThat(codeLengths(generate(ast).second))
                .allSatisfy { assertThat(it).isLessThanOrEqualTo(8_000) }
        }

        @Test
        fun `keeps to the budget past the slot where stores widen`() {
            // a store is one byte up to slot 3, two up to 255 and four beyond, so the
            // prelude costs more per variable the more of them a method reads. Enough of
            // them that getting the width wrong outgrows the slack rather than hiding in it
            val (ast, _) = overVariables(600, 6_000)

            assertThat(codeLengths(generate(ast).second))
                .allSatisfy { assertThat(it).isLessThanOrEqualTo(8_000) }
        }

        @Test
        fun `evaluates correctly when the prelude forces an extra split`() {
            val (ast, names) = overVariables(300, 4_000)

            // every name once, then four thousand more additions of one
            assertThat(eval(ast, names.associateWith { 1.0f })).isEqualTo(names.size + 4_000.0f)
        }

        @Test
        fun `reads the map through one accessor however many methods there are`() {
            assertThat(methodNames(generate(chain(5_000)).second).filter { it == "getFloat" })
                .hasSize(1)
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
