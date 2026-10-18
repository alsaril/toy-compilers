package com.alsaril.scheme.compiler

import com.alsaril.codegen.Compiler.pipeline
import com.alsaril.scheme.SchemeNameException
import com.alsaril.scheme.SchemeRuntimeException
import com.alsaril.scheme.analyser.*
import com.alsaril.scheme.analyser.Bind.Type.DEFINE
import com.alsaril.scheme.analyser.Bind.Type.SET
import com.alsaril.scheme.analyser.ResolvedReference.Location.*
import com.alsaril.scheme.compiler.ClassGenerator.Companion.generate
import com.alsaril.scheme.parser.Cell
import com.alsaril.scheme.parser.Null
import com.alsaril.scheme.parser.Number
import com.alsaril.scheme.runtime.Environment
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Printer.print
import com.alsaril.scheme.runtime.Program
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// Each AST is written by hand as the analyser would resolve the Scheme in the comment above it.
class ClassGeneratorTest {
    @Nested
    inner class Runs {
        @Test
        fun `a closure over an unboxed argument`() {
            // ((lambda (x) ((lambda () x))) 7)
            val inner = lambda(listOf(capture("x", 1)), ref("x"))
            val outer = lambda(listOf(argument("x", 1)), call(inner))

            assertThat(run(call(outer, num(7)))).isEqualTo("7")
        }

        @Test
        fun `a closure over a boxed argument`() {
            // ((lambda (x) ((lambda () x))) 7), with x boxed
            val inner = lambda(listOf(capture("x", 1, boxed = true)), ref("x"))
            val outer = lambda(listOf(argument("x", 1, boxed = true)), call(inner))

            assertThat(run(call(outer, num(7)))).isEqualTo("7")
        }

        @Test
        fun `a set! inside a closure, seen by the enclosing lambda`() {
            // ((lambda (x) ((lambda () (set! x 5))) x) 1)
            val inner = lambda(listOf(capture("x", 1, boxed = true)), Bind(SET, ref("x"), num(5)))
            val outer = lambda(listOf(argument("x", 1, boxed = true)), call(inner), ref("x"))

            assertThat(run(call(outer, num(1)))).isEqualTo("5")
        }

        @Test
        fun `a set! of an argument nothing captures`() {
            // ((lambda (x) (set! x 5) x) 1)
            val outer = lambda(listOf(argument("x", 1)), Bind(SET, ref("x"), num(5)), ref("x"))

            assertThat(run(call(outer, num(1)))).isEqualTo("5")
        }

        @Test
        fun `a variable carried through a lambda that never names it`() {
            // ((((lambda (x) (lambda () (lambda () x))) 7)))
            val inner = lambda(listOf(capture("x", 1)), ref("x"))
            val middle = lambda(listOf(capture("x", 1)), inner)
            val outer = lambda(listOf(argument("x", 1)), middle)

            assertThat(run(call(call(call(outer, num(7)))))).isEqualTo("7")
        }

        @Test
        fun `captures in another order than the parameters`() {
            // ((lambda (a b) ((lambda () (cons b a)))) 1 2)
            val cons = global("cons", 0)
            val inner = lambda(listOf(cons, capture("b", 1), capture("a", 2)), call(ref("cons"), ref("b"), ref("a")))
            val outer = lambda(listOf(cons, argument("a", 1), argument("b", 2)), call(inner))

            assertThat(run(call(outer, num(1), num(2)), listOf(cons))).isEqualTo("(2 . 1)")
        }

        @Test
        fun `a boxed argument read in a branch`() {
            // ((lambda (x) (if #f 0 x)) 1), with x boxed: the else branch starts a new stack map frame
            val outer = lambda(listOf(argument("x", 1, boxed = true)), IfExpression(BooleanConstant.FALSE, num(0), ref("x")))

            assertThat(run(call(outer, num(1)))).isEqualTo("1")
        }

        @Test
        fun `a local defined in the body`() {
            // ((lambda () (define y 1) y))
            val outer = lambda(listOf(local("y", 1)), Bind(DEFINE, ref("y"), num(1)), ref("y"))

            assertThat(run(call(outer))).isEqualTo("1")
        }

        @Test
        fun `a local procedure that calls itself`() {
            // ((lambda () (define (f n) (if (= n 0) 0 (+ n (f (- n 1))))) (f 3)))
            val globals = listOf(global("=", 0), global("+", 1), global("-", 2))
            val f = lambda(
                globals + capture("f", 1, boxed = true) + argument("n", 2),
                IfExpression(
                    call(ref("="), ref("n"), num(0)),
                    num(0),
                    call(ref("+"), ref("n"), call(ref("f"), call(ref("-"), ref("n"), num(1)))),
                ),
            )
            val outer = lambda(globals + local("f", 1, boxed = true), Bind(DEFINE, ref("f"), f), call(ref("f"), num(3)))

            assertThat(run(call(outer), globals)).isEqualTo("6")
        }

        @Test
        fun `a quoted list inside a lambda`() {
            // ((lambda () '(1 2)))
            val outer = lambda(listOf(), Datum(Cell(Number(1), Cell(Number(2), Null))))

            assertThat(run(call(outer))).isEqualTo("(1 2)")
        }

        @Test
        fun `a call with five arguments, the most passed without an array`() {
            // ((lambda (a b c d e) (list e a)) 1 2 3 4 5)
            val list = global("list", 0)
            val parameters = listOf("a", "b", "c", "d", "e").mapIndexed { index, name -> argument(name, index + 1) }
            val outer = lambda(listOf(list) + parameters, call(ref("list"), ref("e"), ref("a")))

            assertThat(run(call(outer, num(1), num(2), num(3), num(4), num(5)), listOf(list))).isEqualTo("(5 1)")
        }

        @Test
        fun `a define at the top level, seen by a later program`() {
            // given (define x 5)
            val env = GlobalEnvironment()
            val x = global("x", 0)

            // when / then
            assertThat(run(Bind(DEFINE, ref("x"), num(5)), listOf(x), env)).isEqualTo("#<unspecified>")
            assertThat(run(ref("x"), listOf(x), env)).isEqualTo("5")
        }

        @Test
        fun `a set! of a global inside a lambda`() {
            // given x is 1
            val env = GlobalEnvironment()
            env.set("x", 1)
            val x = global("x", 0)

            // when ((lambda () (set! x 2) x))
            val outer = lambda(listOf(x), Bind(SET, ref("x"), num(2)), ref("x"))

            // then
            assertThat(run(call(outer), listOf(x), env)).isEqualTo("2")
            assertThat(run(ref("x"), listOf(x), env)).isEqualTo("2")
        }
    }

    @Nested
    inner class Rejects {
        @Test
        fun `a call with the wrong number of arguments`() {
            // ((lambda (a b) a) 1)
            val outer = lambda(listOf(argument("a", 1), argument("b", 2)), ref("a"))

            assertThatExceptionOfType(SchemeRuntimeException::class.java)
                .isThrownBy { run(call(outer, num(1))) }
                .withMessage("procedure (a b): expected 2 arguments, got 1")
        }

        @Test
        fun `a call of something that is not a procedure`() {
            // (1 2)
            assertThatExceptionOfType(SchemeRuntimeException::class.java)
                .isThrownBy { run(call(num(1), num(2))) }
                .withMessage("1 is not a procedure")
        }

        @Test
        fun `a reference to an undefined global`() {
            // x
            assertThatExceptionOfType(SchemeNameException::class.java)
                .isThrownBy { run(ref("x"), listOf(global("x", 0))) }
                .withMessage("x is not defined")
        }

        @Test
        fun `a set! of an undefined global`() {
            // (set! x 2)
            assertThatExceptionOfType(SchemeNameException::class.java)
                .isThrownBy { run(Bind(SET, ref("x"), num(2)), listOf(global("x", 0))) }
                .withMessage("set!: x is not defined")
        }
    }

    private fun run(
        body: Expression,
        globals: List<ResolvedReference> = listOf(),
        env: Environment = GlobalEnvironment(),
    ): String {
        val program = pipeline(
            "",
            { null },
            { generate(GlobalForm(body, Frame(globals))) },
            Program::class.java,
            ClassGenerator.lookup,
        )
        return print(program.run(env))
    }

    private fun global(name: String, index: Int) = ResolvedReference(name, GLOBAL, true, index)

    private fun capture(name: String, slot: Int, boxed: Boolean = false) = ResolvedReference(name, CAPTURE, boxed, slot)

    private fun argument(name: String, slot: Int, boxed: Boolean = false) = ResolvedReference(name, ARGUMENT, boxed, slot)

    private fun local(name: String, slot: Int, boxed: Boolean = false) = ResolvedReference(name, LOCAL, boxed, slot)

    private fun lambda(frame: List<ResolvedReference>, vararg body: Expression) =
        Lambda(frame.filter { it.location == ARGUMENT }.map { it.name }, body.toList(), Frame(frame))

    private fun call(function: Expression, vararg args: Expression) = Call(function, args.toList())

    private fun ref(name: String) = Reference(name)

    private fun num(value: Int) = NumberConstant(value)
}
