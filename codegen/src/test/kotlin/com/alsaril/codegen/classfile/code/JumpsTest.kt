package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.bytesOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JumpsTest {

    @Test
    fun `writes the conditional opcodes`() {
        assertThat(bytecode { goto(0) }).startsWith(*bytesOf(0xA7))
        assertThat(bytecode { ifeq(0) }).startsWith(*bytesOf(0x99))
        assertThat(bytecode { ifne(0) }).startsWith(*bytesOf(0x9A))
        assertThat(bytecode { ifge(0) }).startsWith(*bytesOf(0x9C))
        assertThat(bytecode { ifgt(0) }).startsWith(*bytesOf(0x9D))
        assertThat(bytecode { if_icmplt(0) }).startsWith(*bytesOf(0xA1))
        assertThat(bytecode { if_icmpge(0) }).startsWith(*bytesOf(0xA2))
    }

    @Test
    fun `writes a known destination as an offset from the opcode`() {
        assertThat(bytecode { goto(0) }).containsExactly(*bytesOf(0xA7, 0x00, 0x00))
    }

    @Test
    fun `writes a backwards jump as a negative offset`() {
        // the opcode sits at 1, so jumping to 0 is an offset of -1
        assertThat(bytecode { nop(); goto(0) })
            .containsExactly(*bytesOf(0x00, 0xA7, 0xFF, 0xFF))
    }

    @Test
    fun `emits a placeholder when the destination is unknown`() {
        assertThat(bytecode { goto() }).containsExactly(*bytesOf(0xA7, 0x00, 0x00))
        assertThat(bytecode { ifeq() }).containsExactly(*bytesOf(0x99, 0x00, 0x00))
        assertThat(bytecode { ifne() }).containsExactly(*bytesOf(0x9A, 0x00, 0x00))
        assertThat(bytecode { ifge() }).containsExactly(*bytesOf(0x9C, 0x00, 0x00))
        assertThat(bytecode { ifgt() }).containsExactly(*bytesOf(0x9D, 0x00, 0x00))
        assertThat(bytecode { if_icmplt() }).containsExactly(*bytesOf(0xA1, 0x00, 0x00))
        assertThat(bytecode { if_icmpge() }).containsExactly(*bytesOf(0xA2, 0x00, 0x00))
    }

    @Test
    fun `patches the placeholder once the destination is known`() {
        // given
        val code = bytecode {
            val jump = goto()
            nop()
            jump(loc())
        }

        // then the offset spans the jump instruction and the nop
        assertThat(code).containsExactly(*bytesOf(0xA7, 0x00, 0x04, 0x00))
    }

    @Test
    fun `patches a conditional jump the same way`() {
        // given
        val code = bytecode {
            iconst(0)
            val jump = ifeq()
            `return`()
            jump(loc())
            `return`()
        }

        // then the branch target is measured from the ifeq at offset 1
        assertThat(code).containsExactly(*bytesOf(0x03, 0x99, 0x00, 0x04, 0xB1, 0xB1))
    }

    @Test
    fun `lets several jumps share one target`() {
        // given
        val code = bytecode {
            val first = goto()
            val second = goto()
            val target = loc()
            first(target)
            second(target)
        }

        // then each offset is relative to its own opcode
        assertThat(code).containsExactly(*bytesOf(0xA7, 0x00, 0x06, 0xA7, 0x00, 0x03))
    }

    @Test
    fun `patches a backwards jump to an earlier location`() {
        // given
        val code = bytecode {
            val target = loc()
            nop()
            val jump = goto()
            jump(target)
        }

        // then the jump at offset 1 reaches back to 0
        assertThat(code).containsExactly(*bytesOf(0x00, 0xA7, 0xFF, 0xFF))
    }

    @Test
    fun `leaves nothing to patch when the destination is given upfront`() {
        // given
        val code = bytecode {
            val patch = goto(0)
            patch(999) // the returned function is a no-op
        }

        // then
        assertThat(code).containsExactly(*bytesOf(0xA7, 0x00, 0x00))
    }
}
