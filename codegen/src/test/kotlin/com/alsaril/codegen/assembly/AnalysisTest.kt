package com.alsaril.codegen.assembly

import com.alsaril.codegen.code.*
import com.alsaril.codegen.code.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.AccessFlag.STATIC
import com.alsaril.codegen.classfile.PrimitiveType as ElementType
import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Test
import com.alsaril.codegen.instruction.*

/**
 * Deriving max_stack and the frames walks the code from the entry and from every handler,
 * tracking the type of every value, so it is also where a malformed body is first noticed.
 * What it refuses, and what it says about it.
 */
class AnalysisTest {

    private fun method(
        descriptor: String = "()V",
        hierarchy: ClassHierarchy = LenientHierarchy,
        body: CodeBuilder.() -> Unit,
    ) = classFile("Analysed", "java/lang/Object", hierarchy)
        .method("f", descriptor, STATIC, codeBuilder = body)
        .build()

    @Test
    fun `refuses a body with no instructions at all`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { } }
            .withMessageContaining("must hold at least one instruction")
    }

    @Test
    fun `refuses a conditional jump that was never linked`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iconst(0); +ifeq; +`return` } }
            .withMessageContaining("was never linked to a target")
    }

    @Test
    fun `refuses a goto that was never linked`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +goto } }
            .withMessageContaining("was never linked to a target")
    }

    @Test
    fun `refuses code that runs past its last instruction`() {
        assertThatIllegalStateException()
            .isThrownBy { method { +nop } }
            .withMessage("nop at 0 continues to 1, which is past the last instruction")
    }

    @Test
    fun `refuses two paths that meet at different depths`() {
        // one arm leaves an int behind that the other does not
        assertThatIllegalStateException()
            .isThrownBy {
                method("(I)V") {
                    +iload(0)
                    val jump = +ifeq
                    +iconst(7)
                    val target = +`return`
                    link(jump, target)
                }
            }
            .withMessageContaining("deep on one path and")
    }

    @Test
    fun `refuses an instruction that pops more than the stack holds`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iadd; +`return` } }
            .withMessageContaining("iadd at 0 pops 2 from a stack 0 deep")
    }

    @Test
    fun `refuses dup_x1 with fewer than two values on the stack`() {
        // dup_x1 reaches under the top value, so a lone value is not enough
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iconst(0); +dup_x1; +`return` } }
            .withMessageContaining("dup_x1 at 1 pops 2 from a stack 1 deep")
    }

    @Test
    fun `refuses a field store without its receiver`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iconst(0); +putfield(field(clazz("A"), "x", "I")); +`return` } }
            .withMessageContaining("pops 2 from a stack 1 deep")
    }

    @Test
    fun `refuses a field read without its receiver`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +getfield(field(clazz("A"), "x", "I")); +`return` } }
            .withMessageContaining("pops 1 from a stack 0 deep")
    }

    @Test
    fun `ends a path at areturn`() {
        // anything after it is reached by nothing, which only shows if areturn ends the walk
        assertThatIllegalArgumentException()
            .isThrownBy { method("()Ljava/lang/Object;") { +aconst_null; +areturn; +nop } }
            .withMessageContaining("instruction 2 is unreachable")
    }

    @Test
    fun `refuses an instruction nothing reaches`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +`return`; +nop } }
            .withMessageContaining("instruction 1 is unreachable")
    }

    @Test
    fun `refuses a handler that starts past the last instruction`() {
        // a handler entry is a root of its own, so it is read before the walk can reach it
        val body = builder().apply { +`return` }.build()
            .copy(exceptionHandlers = listOf(ExceptionHandler(0, 1, 9, catchType = null)))

        assertThatIllegalArgumentException()
            .isThrownBy { classFile("Analysed", "java/lang/Object").method("f", "()V", body, STATIC) }
            .withMessageContaining("a handler starts at 9, which is past the last instruction")
    }

    @Test
    fun `refuses a link from something that is not a jump`() {
        assertThatIllegalArgumentException()
            .isThrownBy {
                builder().apply {
                    val from = +nop
                    link(from, +nop)
                }.build().bytecode()
            }
            .withMessageContaining("is not a jump")
    }

    @Test
    fun `refuses a value of the wrong type`() {
        // the int on top is taken first, so the complaint is about the float under it
        assertThatIllegalArgumentException()
            .isThrownBy { method { +fconst(1); +iconst(1); +iadd; +`return` } }
            .withMessageContaining("iadd at 2 expects INTEGER on the stack, but finds FLOAT")
    }

    @Test
    fun `refuses a read of a local nothing wrote`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iload(0); +`return` } }
            .withMessageContaining("at 0 reads local 0, but only 0 local slots are defined")
    }

    @Test
    fun `refuses a read of a local as the wrong type`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +fconst(1); +fstore(0); +iload(0); +`return` } }
            .withMessageContaining("at 2 reads local 0 as INTEGER, but it holds FLOAT")
    }

    @Test
    fun `refuses a reference read from a local holding a number`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iconst(1); +istore(0); +aload(0); +`return` } }
            .withMessageContaining("at 2 expects a reference in local 0, but it holds INTEGER")
    }

    @Test
    fun `refuses a reference read from a local nothing wrote`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +aload(2); +`return` } }
            .withMessageContaining("at 0 reads local 2, but only 0 local slots are defined")
    }

    @Test
    fun `refuses to store a number as a long`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iconst(1); +lstore(0); +`return` } }
            .withMessageContaining("at 1 expects LONG on the stack, but finds INTEGER")
    }

    @Test
    fun `refuses a number where a class is declared`() {
        assertThatIllegalArgumentException()
            .isThrownBy {
                method("()I") {
                    +iconst(1)
                    +invokestatic(clazz("java/util/Objects"), "hashCode", "(Ljava/lang/Object;)I")
                    +ireturn
                }
            }
            .withMessageContaining("expects ReferenceType(descriptor=java/lang/Object) on the stack, but finds INTEGER")
    }

    @Test
    fun `refuses to store a number as a reference`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iconst(1); +astore(0); +`return` } }
            .withMessageContaining("at 1 stores a reference, but finds INTEGER")
    }

    @Test
    fun `refuses pop of a two slot value`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +lconst(0); +pop; +`return` } }
            .withMessageContaining("pop at 1 drops a one slot value, but finds LONG")
    }

    @Test
    fun `refuses pop on an empty stack`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +pop; +`return` } }
            .withMessageContaining("pop at 0 pops 1 from a stack 0 deep")
    }

    @Test
    fun `refuses pop2 of a one slot value over a two slot one`() {
        // pop2 drops either one long or two ints, never half of each
        assertThatIllegalArgumentException()
            .isThrownBy { method { +lconst(0); +iconst(0); +pop2; +`return` } }
            .withMessageContaining("pop2 at 2 drops two one slot values, but finds LONG under INTEGER")
    }

    @Test
    fun `refuses pop2 with only one one slot value`() {
        // a single int is half of what pop2 drops, where a long would do alone
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iconst(0); +pop2; +`return` } }
            .withMessageContaining("pop2 at 1 pops 2 from a stack 1 deep")
    }

    @Test
    fun `refuses dup of a two slot value`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +lconst(0); +dup; +`return` } }
            .withMessageContaining("dup at 1 copies a one slot value, but finds LONG")
    }

    @Test
    fun `refuses dup2 of a one slot value over a two slot one`() {
        // dup2 copies either one long or two ints, never half of each
        assertThatIllegalArgumentException()
            .isThrownBy { method { +lconst(0); +iconst(0); +dup2; +`return` } }
            .withMessageContaining("dup2 at 2 copies two one slot values, but finds LONG under INTEGER")
    }

    @Test
    fun `refuses dup_x1 reaching under a two slot value`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +lconst(0); +iconst(0); +dup_x1; +`return` } }
            .withMessageContaining("dup_x1 at 2 works on two one slot values, but finds LONG and INTEGER")
    }

    @Test
    fun `refuses dup_x1 with a two slot value on top`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iconst(0); +lconst(0); +dup_x1; +`return` } }
            .withMessageContaining("dup_x1 at 2 works on two one slot values, but finds INTEGER and LONG")
    }

    @Test
    fun `refuses dup2_x1 with only two one slot values`() {
        // two ints are what it copies, so it needs a third one under them
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iconst(0); +iconst(0); +dup2_x1; +`return` } }
            .withMessageContaining("dup2_x1 at 2 pops 3 from a stack 2 deep")
    }

    @Test
    fun `refuses dup2_x1 with nothing under a two slot value`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +lconst(0); +dup2_x1; +`return` } }
            .withMessageContaining("dup2_x1 at 1 pops 2 from a stack 1 deep")
    }

    @Test
    fun `refuses dup2_x1 of a one slot value over a two slot one`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iconst(0); +lconst(0); +iconst(0); +dup2_x1; +`return` } }
            .withMessageContaining("dup2_x1 at 3 copies two one slot values, but finds LONG under INTEGER")
    }

    @Test
    fun `refuses dup2_x1 reaching under two ints into a two slot value`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +lconst(0); +iconst(0); +iconst(0); +dup2_x1; +`return` } }
            .withMessageContaining("dup2_x1 at 3 reaches under INTEGER, so it needs a one slot value there, but finds LONG")
    }

    @Test
    fun `refuses dup2_x1 reaching under a two slot value into another`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +lconst(0); +lconst(1); +dup2_x1; +`return` } }
            .withMessageContaining("dup2_x1 at 2 reaches under LONG, so it needs a one slot value there, but finds LONG")
    }

    @Test
    fun `refuses swap with fewer than two values on the stack`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iconst(0); +swap; +`return` } }
            .withMessageContaining("swap at 1 pops 2 from a stack 1 deep")
    }

    @Test
    fun `refuses swap with a two slot value under the top`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +lconst(0); +iconst(0); +swap; +`return` } }
            .withMessageContaining("swap at 2 works on two one slot values, but finds LONG and INTEGER")
    }

    @Test
    fun `refuses swap with a two slot value on top`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iconst(0); +lconst(0); +swap; +`return` } }
            .withMessageContaining("swap at 2 works on two one slot values, but finds INTEGER and LONG")
    }

    @Test
    fun `refuses dup_x2 with a two slot value on top`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iconst(0); +lconst(0); +dup_x2; +`return` } }
            .withMessageContaining("dup_x2 at 2 copies a one slot value, but finds LONG")
    }

    @Test
    fun `refuses dup_x2 reaching half way into a two slot value`() {
        // under two ints it has to reach a third one slot value, not the top half of a long
        assertThatIllegalArgumentException()
            .isThrownBy { method { +lconst(0); +iconst(0); +iconst(0); +dup_x2; +`return` } }
            .withMessageContaining("dup_x2 at 3 reaches under INTEGER, so it needs a one slot value there, but finds LONG")
    }

    @Test
    fun `refuses dup_x2 with only two one slot values under it`() {
        // under two one slot values it needs a third, where a two slot one would do alone
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iconst(0); +iconst(0); +dup_x2; +`return` } }
            .withMessageContaining("dup_x2 at 2 pops 3 from a stack 2 deep")
    }

    @Test
    fun `refuses two paths that meet with different types`() {
        // one arm leaves an int where the other leaves a float
        assertThatIllegalStateException()
            .isThrownBy {
                method("(I)V") {
                    +iload(0)
                    val otherwise = +ifeq
                    +iconst(1)
                    val done = +goto
                    link(otherwise, +fconst(1))
                    link(done, +`return`)
                }
            }
            .withMessageContaining("return at 5 is reached with a stack of")
            .withMessageContaining("on one path and")
    }

    @Test
    fun `refuses anewarray of a length that is not an int`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +aconst_null; +anewarray(clazz("java/lang/String")); +`return` } }
            .withMessageContaining("at 1 expects INTEGER on the stack, but finds NULL")
    }

    @Test
    fun `refuses aaload from an array of numbers`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iconst(1); +newarray(ElementType.INTEGER); +iconst(0); +aaload; +`return` } }
            .withMessageContaining("aaload at 3 expects an array of references on the stack, but finds ReferenceType(descriptor=[I)")
    }

    @Test
    fun `refuses aaload from something that is not an array`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +ldc(string("s")); +iconst(0); +aaload; +`return` } }
            .withMessageContaining("aaload at 2 expects an array of references on the stack, but finds ReferenceType(descriptor=java/lang/String)")
    }

    @Test
    fun `refuses aastore into an array of numbers`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +iconst(1); +newarray(ElementType.INTEGER); +iconst(0); +aconst_null; +aastore; +`return` } }
            .withMessageContaining("aastore at 4 expects an array of references on the stack, but finds ReferenceType(descriptor=[I)")
    }

    @Test
    fun `refuses aastore of a number`() {
        assertThatIllegalArgumentException()
            .isThrownBy {
                method { +iconst(1); +anewarray(clazz("java/lang/Object")); +iconst(0); +iconst(1); +aastore; +`return` }
            }
            .withMessageContaining("aastore at 4 expects AnyReference on the stack, but finds INTEGER")
    }

    @Test
    fun `leaves it to the running code whether aastore's array takes the class it stores`() {
        // given a hierarchy that takes nothing but an exact match, the verifier still only asks
        // aastore for a reference, and a wrong one is an ArrayStoreException when it runs
        val strict = object : ClassHierarchy {
            override fun isAssignable(from: String, to: String) = from == to
            override fun commonSuperclass(a: String, b: String) = a
        }

        // then an Object goes into a String[]
        assertThatNoException().isThrownBy {
            method("(Ljava/lang/Object;)V", strict) {
                +iconst(1)
                +anewarray(clazz("java/lang/String"))
                +iconst(0)
                +aload(0)
                +aastore
                +`return`
            }
        }
    }

    @Test
    fun `refuses an array baload does not serve, given a hierarchy that says so`() {
        // given a hierarchy that takes nothing but an exact match
        val strict = object : ClassHierarchy {
            override fun isAssignable(from: String, to: String) = from == to
            override fun commonSuperclass(a: String, b: String) = a
        }

        // then an int[] is neither the byte[] nor the boolean[] it reads
        assertThatIllegalArgumentException()
            .isThrownBy {
                method("()I", strict) {
                    +iconst(1)
                    +newarray(ElementType.INTEGER)
                    +iconst(0)
                    +baload
                    +ireturn
                }
            }
            .withMessageContaining("baload at 3 expects one of [B, [Z on the stack, but finds ReferenceType(descriptor=[I)")
    }

    @Test
    fun `refuses a reference and a number meeting where two paths join`() {
        assertThatIllegalStateException()
            .isThrownBy {
                method("(I)V") {
                    +iload(0)
                    val otherwise = +ifeq
                    +ldc(string("s"))
                    val done = +goto
                    link(otherwise, +iconst(1))
                    link(done, +`return`)
                }
            }
            .withMessageContaining("return at 5 is reached with a stack of")
    }

    @Test
    fun `refuses to hand on an object whose constructor never ran`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method("()Ljava/lang/Object;") { +new(clazz("java/lang/Object")); +areturn } }
            .withMessageContaining("areturn at 1 expects AnyReference on the stack, but finds Uninitialized(offset=0)")
    }

    @Test
    fun `refuses a constructor call on an object that is already built`() {
        assertThatIllegalArgumentException()
            .isThrownBy {
                method("(Ljava/lang/Object;)V") {
                    +aload(0)
                    +invokespecial(clazz("java/lang/Object"), "<init>", "()V")
                    +`return`
                }
            }
            .withMessageContaining("expects uninitializedThis or an object fresh from new under its arguments")
    }

    @Test
    fun `refuses an argument the hierarchy says does not fit`() {
        // given a hierarchy that takes nothing but an exact match
        val strict = object : ClassHierarchy {
            override fun isAssignable(from: String, to: String) = from == to
            override fun commonSuperclass(a: String, b: String) = a
        }

        // then a String is not accepted where the call declares an Object
        assertThatIllegalArgumentException()
            .isThrownBy {
                method("()I", strict) {
                    +ldc(string("s"))
                    +invokestatic(clazz("java/util/Objects"), "hashCode", "(Ljava/lang/Object;)I")
                    +ireturn
                }
            }
            .withMessageContaining("expects ReferenceType(descriptor=java/lang/Object) on the stack")
            .withMessageContaining("finds ReferenceType(descriptor=java/lang/String)")
    }
}
