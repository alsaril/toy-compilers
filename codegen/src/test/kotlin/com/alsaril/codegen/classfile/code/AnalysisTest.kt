package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.classfile.ClassFileBuilder.Companion.classFile
import com.alsaril.codegen.classfile.MethodAccessFlag.STATIC
import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import com.alsaril.codegen.classfile.code.instruction.*

/**
 * Deriving max_stack walks the code from the entry and from every handler, so it is also
 * where a malformed body is first noticed. What it refuses, and what it says about it.
 */
class AnalysisTest {

    private fun method(descriptor: String = "()V", body: CodeBuilder.() -> Unit) =
        classFile("Analysed", "java/lang/Object")
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
            .withMessageContaining("past the last instruction")
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
    fun `refuses an instruction nothing reaches`() {
        assertThatIllegalArgumentException()
            .isThrownBy { method { +`return`; +nop } }
            .withMessageContaining("instruction 1 is unreachable")
    }

    @Test
    fun `refuses a handler that starts past the last instruction`() {
        // a handler entry is a root of its own, so it is read before the walk can reach it
        val body = builder().apply { +`return` }.build()
            .copy(exceptionHandlers = listOf(ExceptionHandler(0, 1, 9, catchType = 0)))

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
    fun `names the instruction a complaint is about`() {
        // the message has to identify which instruction, not just that one was wrong
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { method { +nop } }
            .withMessageContaining("nop at 0")
    }
}
