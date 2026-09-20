package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.constantpool.ConstantClassInfo
import com.alsaril.codegen.constantpool.ConstantUtf8Info
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

/**
 * A handler covers the half open range [from, to) and sends a throw to a third label.
 * All three name instructions that have already been added, so a handler is recorded in
 * one go rather than patched later. The positions are instruction indices until the
 * fragment is laid out.
 */
class ExceptionsTest {

    @Test
    fun `records no handlers for code without a catch`() {
        assertThat(handlers { nop() }).isEmpty()
    }

    @Test
    fun `spans the code between its two bounds`() {
        // given a range closing where the handler begins, which is the usual shape
        val recorded = handlers {
            nop()
            val from = nop()
            nop()
            val caught = nop()
            `catch`(from, to = caught, handler = caught, type = null)
        }

        // then the range covers the two protected nops and stops before the handler
        assertThat(recorded).containsExactly(ExceptionHandler(1, 3, 3, catchType = 0))
    }

    @Test
    fun `catches everything when no type is given`() {
        // catch_type 0 is the JVMS encoding for "any throwable", which is what a
        // finally block needs
        val recorded = handlers {
            val from = nop()
            val caught = nop()
            `catch`(from, caught, caught, type = null)
        }

        assertThat(recorded).containsExactly(ExceptionHandler(0, 1, 1, catchType = 0))
    }

    @Test
    fun `refuses a range that covers no instruction`() {
        // given a range closed at the instruction it opened on
        assertThatIllegalArgumentException()
            .isThrownBy { handlers { val at = nop(); `catch`(at, at, at, type = null) } }
            .withMessageContaining("[0, 0) covers no instruction")
    }

    @Test
    fun `names the caught type through a constant pool class entry`() {
        // given
        val cp = UpdatableConstantPool()

        // when
        val recorded = builder(cp).apply {
            val from = nop()
            val caught = nop()
            `catch`(from, caught, caught, clazz("java/lang/Throwable"))
        }.build().exceptionHandlers

        // then the class is registered once and the handler refers to it by index
        assertThat(recorded).containsExactly(ExceptionHandler(0, 1, 1, catchType = 2))
        assertThat(cp.build().entries).containsExactly(
            ConstantUtf8Info("java/lang/Throwable"),
            ConstantClassInfo(nameIndex = 1),
        )
    }

    @Test
    fun `points the handler wherever it is told to`() {
        // given code between the end of the range and the handler itself
        val recorded = handlers {
            val from = nop()
            val to = nop()
            nop()
            val caught = nop()
            `catch`(from, to, caught, type = null)
        }

        // then the range still ends where it was closed
        assertThat(recorded).containsExactly(ExceptionHandler(0, 1, 3, catchType = 0))
    }

    @Test
    fun `lets the handler sit inside the code the range protects`() {
        // a handler pointing backwards is legal, and is how a retry loop is written
        val recorded = handlers {
            val from = nop()
            val to = nop()
            `catch`(from, to, handler = from, type = null)
        }

        assertThat(recorded).containsExactly(ExceptionHandler(0, 1, 0, catchType = 0))
    }

    @Test
    fun `keeps several handlers in the order they were recorded`() {
        // given two ranges, the inner one recorded first
        val recorded = handlers {
            val outer = nop()
            val inner = nop()
            val end = nop()
            val outerCaught = nop()
            `catch`(inner, end, end, type = null)
            `catch`(outer, end, outerCaught, type = null)
        }

        // then the inner one comes first, which is the order the jvm searches them in
        assertThat(recorded).containsExactly(
            ExceptionHandler(1, 2, 2, catchType = 0),
            ExceptionHandler(0, 2, 3, catchType = 0),
        )
    }

    @Test
    fun `lets two ranges share one handler`() {
        // given
        val recorded = handlers {
            val first = nop()
            val firstEnd = nop()
            val second = nop()
            val target = nop()
            `catch`(first, firstEnd, target, type = null)
            `catch`(second, target, target, type = null)
        }

        // then
        assertThat(recorded).containsExactly(
            ExceptionHandler(0, 1, 3, catchType = 0),
            ExceptionHandler(2, 3, 3, catchType = 0),
        )
    }

    @Test
    fun `measures the range in instructions rather than bytes`() {
        // given a three byte goto inside the protected range
        val recorded = handlers {
            val from = goto()
            val caught = nop()
            `catch`(from, caught, caught, type = null)
        }

        // then the range is one instruction wide, whatever that instruction encodes to
        assertThat(recorded).containsExactly(ExceptionHandler(0, 1, 1, catchType = 0))
    }

    @Test
    fun `lays the range out in bytes once the code is emitted`() {
        // given the same three byte goto inside the range
        val fragment = builder().apply {
            val from = goto()
            val caught = nop()
            `catch`(from, caught, caught, type = null)
        }.build()

        // then the indices become the offsets the class file names
        assertThat(handlersOf(fragment)).containsExactly(ExceptionHandler(0, 3, 3, catchType = 0))
    }
}
