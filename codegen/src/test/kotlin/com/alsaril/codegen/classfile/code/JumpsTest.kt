package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.bytesOf
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

class JumpsTest {

    @Test
    fun `writes the conditional opcodes`() {
        assertThat(bytecode { goto() }).startsWith(*bytesOf(0xA7))
        assertThat(bytecode { ifeq() }).startsWith(*bytesOf(0x99))
        assertThat(bytecode { ifne() }).startsWith(*bytesOf(0x9A))
        assertThat(bytecode { ifge() }).startsWith(*bytesOf(0x9C))
        assertThat(bytecode { ifgt() }).startsWith(*bytesOf(0x9D))
        assertThat(bytecode { if_icmplt() }).startsWith(*bytesOf(0xA1))
        assertThat(bytecode { if_icmpge() }).startsWith(*bytesOf(0xA2))
        assertThat(bytecode { ifnull() }).startsWith(*bytesOf(0xC6))
        assertThat(bytecode { ifnotnull() }).startsWith(*bytesOf(0xC7))
    }

    @Test
    fun `writes a known destination as an offset from the opcode`() {
        // a jump to itself is the one destination that is an offset of zero
        assertThat(bytecode { val jump = goto(); link(jump, jump) })
            .containsExactly(*bytesOf(0xA7, 0x00, 0x00))
    }

    @Test
    fun `writes a backwards jump as a negative offset`() {
        // the opcode sits at 1, so jumping to 0 is an offset of -1
        assertThat(bytecode { val target = nop(); goto(target) })
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
        assertThat(bytecode { ifnull() }).containsExactly(*bytesOf(0xC6, 0x00, 0x00))
        assertThat(bytecode { ifnotnull() }).containsExactly(*bytesOf(0xC7, 0x00, 0x00))
    }

    @Test
    fun `patches the placeholder once the destination is known`() {
        // given
        val code = bytecode {
            val jump = goto()
            nop()
            link(jump, nop())
        }

        // then the offset spans the jump instruction and the nop
        assertThat(code).containsExactly(*bytesOf(0xA7, 0x00, 0x04, 0x00, 0x00))
    }

    @Test
    fun `patches a conditional jump the same way`() {
        // given
        val code = bytecode {
            iconst(0)
            val jump = ifeq()
            `return`()
            link(jump, `return`())
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
            val target = nop()
            link(first, target)
            link(second, target)
        }

        // then each offset is relative to its own opcode
        assertThat(code).containsExactly(*bytesOf(0xA7, 0x00, 0x06, 0xA7, 0x00, 0x03, 0x00))
    }

    @Test
    fun `patches a backwards jump to an earlier location`() {
        // given
        val code = bytecode {
            val target = nop()
            val jump = goto()
            link(jump, target)
        }

        // then the jump at offset 1 reaches back to 0
        assertThat(code).containsExactly(*bytesOf(0x00, 0xA7, 0xFF, 0xFF))
    }

    @Test
    fun `leaves nothing to patch when the destination is given upfront`() {
        // given the destination handed to the jump itself rather than linked afterwards
        val code = bytecode {
            val target = nop()
            goto(target)
        }

        // then
        assertThat(code).containsExactly(*bytesOf(0x00, 0xA7, 0xFF, 0xFF))
    }

    @Test
    fun `rejects a branch offset past a signed short`() {
        assertThatExceptionOfType(IllegalArgumentException::class.java)
            .isThrownBy { bytecode { val target = nop(); repeat(40_000) { nop() }; goto(target) } }
            .withMessageContaining("does not fit an s2")
    }

    @Test
    fun `rejects a patched branch offset past a signed short`() {
        assertThatExceptionOfType(IllegalArgumentException::class.java)
            .isThrownBy { bytecode { val jump = goto(); repeat(40_000) { nop() }; link(jump, nop()) } }
            .withMessageContaining("does not fit an s2")
    }
}
