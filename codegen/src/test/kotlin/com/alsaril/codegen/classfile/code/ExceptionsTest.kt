package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.classfile.attributes.ExceptionHandler
import com.alsaril.codegen.constantpool.ConstantClassInfo
import com.alsaril.codegen.constantpool.ConstantUtf8Info
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A handler covers the half open range [startPc, endPc), so `try` marks the start and
 * `catch` closes the range at the point it is called. Where the handler itself sits is
 * only known once the code after the range has been emitted, so it is patched in later,
 * the same way a forward jump is.
 */
class ExceptionsTest {

    @Test
    fun `records no handlers for code without a try`() {
        assertThat(handlers { nop() }).isEmpty()
    }

    @Test
    fun `spans the code between the try and the catch`() {
        // given
        val recorded = handlers {
            nop()
            val from = `try`()
            nop()
            nop()
            val handler = `catch`(from, type = null)
            handler(loc())
        }

        // then the range covers the two protected nops and stops before the handler
        assertThat(recorded).containsExactly(ExceptionHandler(1, 3, 3, catchType = 0))
    }

    @Test
    fun `catches everything when no type is given`() {
        // catch_type 0 is the JVMS encoding for "any throwable", which is what a
        // finally block needs
        assertThat(handlers { `catch`(`try`(), type = null)(loc()) })
            .containsExactly(ExceptionHandler(0, 0, 0, catchType = 0))
    }

    @Test
    fun `names the caught type through a constant pool class entry`() {
        // given
        val cp = UpdatableConstantPool()

        // when
        val recorded = builder(cp).apply {
            val from = `try`()
            nop()
            `catch`(from, clazz("java/lang/Throwable"))(loc())
        }.build().exceptionHandlers

        // then the class is registered once and the handler refers to it by index
        assertThat(recorded).containsExactly(ExceptionHandler(0, 1, 1, catchType = 2))
        assertThat(cp.build().entries).containsExactly(
            ConstantUtf8Info("java/lang/Throwable"),
            ConstantClassInfo(nameIndex = 1),
        )
    }

    @Test
    fun `points the handler wherever it is patched to`() {
        // given code between the end of the range and the handler itself
        val recorded = handlers {
            val from = `try`()
            nop()
            val handler = `catch`(from, type = null)
            nop()
            nop()
            handler(loc())
        }

        // then the range still ends where catch was called
        assertThat(recorded).containsExactly(ExceptionHandler(0, 1, 3, catchType = 0))
    }

    @Test
    fun `lets the handler sit inside the code the range protects`() {
        // a handler patched backwards is legal, and is how a retry loop is written
        val recorded = handlers {
            val target = loc()
            val from = `try`()
            nop()
            `catch`(from, type = null)(target)
        }

        assertThat(recorded).containsExactly(ExceptionHandler(0, 1, 0, catchType = 0))
    }

    @Test
    fun `records nothing until the handler is patched`() {
        // given a range that is closed but never given a handler
        val recorded = handlers {
            val from = `try`()
            nop()
            `catch`(from, type = null)
        }

        // then nothing is written, rather than a handler pointing at zero
        assertThat(recorded).isEmpty()
    }

    @Test
    fun `keeps several handlers in the order they were patched`() {
        // given two ranges, the second closed first
        val recorded = handlers {
            val outer = `try`()
            nop()
            val inner = `try`()
            nop()
            val innerHandler = `catch`(inner, type = null)
            val outerHandler = `catch`(outer, type = null)
            innerHandler(loc())
            nop()
            outerHandler(loc())
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
            val first = `try`()
            nop()
            val a = `catch`(first, type = null)
            nop()
            val second = `try`()
            nop()
            val b = `catch`(second, type = null)
            val target = loc()
            a(target)
            b(target)
        }

        // then
        assertThat(recorded).containsExactly(
            ExceptionHandler(0, 1, 3, catchType = 0),
            ExceptionHandler(2, 3, 3, catchType = 0),
        )
    }

    @Test
    fun `measures the range in bytes rather than instructions`() {
        // given a three byte goto inside the protected range
        val recorded = handlers {
            val from = `try`()
            goto(0)
            val handler = `catch`(from, type = null)
            handler(loc())
        }

        // then
        assertThat(recorded).containsExactly(ExceptionHandler(0, 3, 3, catchType = 0))
    }

    @Test
    fun `reaches the fragment a handler was patched after it was built`() {
        // given the code is frozen before the handler location is known
        val builder = builder()
        val from = builder.`try`()
        builder.nop()
        val handler = builder.`catch`(from, type = null)
        val fragment = builder.build()

        // when
        handler(builder.loc())

        // then the fragment sees the handler the same way it sees a late jump patch
        assertThat(fragment.exceptionHandlers).containsExactly(ExceptionHandler(0, 1, 1, 0))
    }
}
