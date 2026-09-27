package com.alsaril.codegen.instruction

import com.alsaril.codegen.bytesOf
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.alsaril.codegen.code.*
import com.alsaril.codegen.classfile.PrimitiveType as ElementType
import com.alsaril.codegen.constantpool.DataPointer
import com.alsaril.codegen.verification.AnyReference
import com.alsaril.codegen.verification.PrimitiveType.FLOAT
import com.alsaril.codegen.verification.PrimitiveType.INTEGER
import com.alsaril.codegen.verification.PrimitiveType.LONG
import com.alsaril.codegen.verification.PrimitiveType.NULL
import com.alsaril.codegen.verification.PrimitiveType.VOID
import com.alsaril.codegen.verification.ReferenceType

/** Opcode values are the ones listed in JVMS 6.5. */
class InstructionTest {

    @Test
    fun `writes the single byte instructions`() {
        assertThat(bytecode { +nop }).containsExactly(*bytesOf(0x00))
        assertThat(bytecode { +aconst_null }).containsExactly(*bytesOf(0x01))
        assertThat(bytecode { +baload }).containsExactly(*bytesOf(0x33))
        assertThat(bytecode { +bastore }).containsExactly(*bytesOf(0x54))
        assertThat(bytecode { +dup }).containsExactly(*bytesOf(0x59))
        assertThat(bytecode { +dup_x1 }).containsExactly(*bytesOf(0x5A))
        assertThat(bytecode { +dup2 }).containsExactly(*bytesOf(0x5C))
        assertThat(bytecode { +dup_x2 }).containsExactly(*bytesOf(0x5B))
        assertThat(bytecode { +iaload }).containsExactly(*bytesOf(0x2E))
        assertThat(bytecode { +iastore }).containsExactly(*bytesOf(0x4F))
        assertThat(bytecode { +iadd }).containsExactly(*bytesOf(0x60))
        assertThat(bytecode { +isub }).containsExactly(*bytesOf(0x64))
        assertThat(bytecode { +fadd }).containsExactly(*bytesOf(0x62))
        assertThat(bytecode { +fsub }).containsExactly(*bytesOf(0x66))
        assertThat(bytecode { +fmul }).containsExactly(*bytesOf(0x6A))
        assertThat(bytecode { +fdiv }).containsExactly(*bytesOf(0x6E))
        assertThat(bytecode { +fneg }).containsExactly(*bytesOf(0x76))
        assertThat(bytecode { +ireturn }).containsExactly(*bytesOf(0xAC))
        assertThat(bytecode { +freturn }).containsExactly(*bytesOf(0xAE))
        assertThat(bytecode { +areturn }).containsExactly(*bytesOf(0xB0))
        assertThat(bytecode { +`return` }).containsExactly(*bytesOf(0xB1))
        assertThat(bytecode { +athrow }).containsExactly(*bytesOf(0xBF))
    }

    @Nested
    inner class Fconst {

        @Test
        fun `writes the compact float constants`() {
            assertThat(bytecode { +fconst(0) }).containsExactly(*bytesOf(0x0B))
            assertThat(bytecode { +fconst(1) }).containsExactly(*bytesOf(0x0C))
            assertThat(bytecode { +fconst(2) }).containsExactly(*bytesOf(0x0D))
        }

        @Test
        fun `rejects a float constant that needs a constant pool entry`() {
            assertThatIllegalArgumentException().isThrownBy { bytecode { +fconst(3) } }
            assertThatIllegalArgumentException().isThrownBy { bytecode { +fconst(-1) } }
        }
    }

    @Nested
    inner class Lconst {

        @Test
        fun `writes the compact long constants`() {
            assertThat(bytecode { +lconst(0) }).containsExactly(*bytesOf(0x09))
            assertThat(bytecode { +lconst(1) }).containsExactly(*bytesOf(0x0A))
        }

        @Test
        fun `rejects a long constant that needs a constant pool entry`() {
            assertThatIllegalArgumentException()
                .isThrownBy { bytecode { +lconst(2) } }
                .withMessage("2 is out of range for lconst, ldc2_w should be used")
            assertThatIllegalArgumentException().isThrownBy { bytecode { +lconst(-1) } }
        }
    }

    @Test
    fun `appends instructions in order`() {
        assertThat(bytecode { +iadd; +isub; +`return` })
            .containsExactly(*bytesOf(0x60, 0x64, 0xB1))
    }

    @Nested
    inner class Iconst {

        @Test
        fun `uses the compact form from minus one to five`() {
            assertThat(bytecode { +iconst(-1) }).containsExactly(*bytesOf(0x02))
            assertThat(bytecode { +iconst(0) }).containsExactly(*bytesOf(0x03))
            assertThat(bytecode { +iconst(5) }).containsExactly(*bytesOf(0x08))
        }

        @Test
        fun `falls back to bipush outside the compact range`() {
            assertThat(bytecode { +iconst(6) }).containsExactly(*bytesOf(0x10, 0x06))
            assertThat(bytecode { +iconst(-2) }).containsExactly(*bytesOf(0x10, 0xFE))
            assertThat(bytecode { +iconst(127) }).containsExactly(*bytesOf(0x10, 0x7F))
            assertThat(bytecode { +iconst(-128) }).containsExactly(*bytesOf(0x10, 0x80))
        }

        @Test
        fun `falls back to sipush outside the byte range`() {
            assertThat(bytecode { +iconst(128) }).containsExactly(*bytesOf(0x11, 0x00, 0x80))
            assertThat(bytecode { +iconst(-129) }).containsExactly(*bytesOf(0x11, 0xFF, 0x7F))
            assertThat(bytecode { +iconst(32767) }).containsExactly(*bytesOf(0x11, 0x7F, 0xFF))
            assertThat(bytecode { +iconst(-32768) }).containsExactly(*bytesOf(0x11, 0x80, 0x00))
        }

        @Test
        fun `hands back the label of the instruction it added`() {
            // like every other emitter, so a constant can be a branch target
            assertThat(bytecode { val target = +iconst(0); link(+goto, target) })
                .containsExactly(*bytesOf(0x03, 0xA7, 0xFF, 0xFF))
        }

        @Test
        fun `rejects a value that needs a constant pool entry`() {
            assertThatIllegalArgumentException().isThrownBy { bytecode { +iconst(32768) } }
            assertThatIllegalArgumentException().isThrownBy { bytecode { +iconst(-32769) } }
        }
    }

    @Nested
    inner class Ldc {

        @Test
        fun `uses the single byte form for a low pool index`() {
            assertThat(bytecode { +ldc(DataPointer(1, INTEGER)) }).containsExactly(*bytesOf(0x12, 0x01))
            assertThat(bytecode { +ldc(DataPointer(255, INTEGER)) }).containsExactly(*bytesOf(0x12, 0xFF))
        }

        @Test
        fun `switches to the wide form past a single byte index`() {
            assertThat(bytecode { +ldc(DataPointer(256, INTEGER)) })
                .containsExactly(*bytesOf(0x13, 0x01, 0x00))
            assertThat(bytecode { +ldc(DataPointer(65535, INTEGER)) })
                .containsExactly(*bytesOf(0x13, 0xFF, 0xFF))
        }
    }

    @Nested
    inner class LoadsAndStores {

        @Test
        fun `uses the indexed opcodes for the first four slots`() {
            assertThat(bytecode { +iload(0) }).containsExactly(*bytesOf(0x1A))
            assertThat(bytecode { +iload(3) }).containsExactly(*bytesOf(0x1D))
            assertThat(bytecode { +fload(0) }).containsExactly(*bytesOf(0x22))
            assertThat(bytecode { +fload(3) }).containsExactly(*bytesOf(0x25))
            assertThat(bytecode { +aload(0) }).containsExactly(*bytesOf(0x2A))
            assertThat(bytecode { +aload(3) }).containsExactly(*bytesOf(0x2D))
            assertThat(bytecode { +istore(0) }).containsExactly(*bytesOf(0x3B))
            assertThat(bytecode { +istore(3) }).containsExactly(*bytesOf(0x3E))
            assertThat(bytecode { +lstore(0) }).containsExactly(*bytesOf(0x3F))
            assertThat(bytecode { +lstore(3) }).containsExactly(*bytesOf(0x42))
            assertThat(bytecode { +fstore(0) }).containsExactly(*bytesOf(0x43))
            assertThat(bytecode { +fstore(3) }).containsExactly(*bytesOf(0x46))
            assertThat(bytecode { +astore(0) }).containsExactly(*bytesOf(0x4B))
            assertThat(bytecode { +astore(3) }).containsExactly(*bytesOf(0x4E))
        }

        @Test
        fun `switches to the operand form from the fifth slot`() {
            assertThat(bytecode { +iload(4) }).containsExactly(*bytesOf(0x15, 0x04))
            assertThat(bytecode { +fload(4) }).containsExactly(*bytesOf(0x17, 0x04))
            assertThat(bytecode { +aload(4) }).containsExactly(*bytesOf(0x19, 0x04))
            assertThat(bytecode { +istore(4) }).containsExactly(*bytesOf(0x36, 0x04))
            assertThat(bytecode { +lstore(4) }).containsExactly(*bytesOf(0x37, 0x04))
            assertThat(bytecode { +fstore(4) }).containsExactly(*bytesOf(0x38, 0x04))
            assertThat(bytecode { +astore(4) }).containsExactly(*bytesOf(0x3A, 0x04))
        }

        @Test
        fun `uses the highest single byte index`() {
            assertThat(bytecode { +iload(255) }).containsExactly(*bytesOf(0x15, 0xFF))
        }

        @Test
        fun `switches to the wide form past a single byte index`() {
            // wide, then the operand form of the opcode, then the index as a u2
            assertThat(bytecode { +iload(256) }).containsExactly(*bytesOf(0xC4, 0x15, 0x01, 0x00))
            assertThat(bytecode { +fload(256) }).containsExactly(*bytesOf(0xC4, 0x17, 0x01, 0x00))
            assertThat(bytecode { +aload(256) }).containsExactly(*bytesOf(0xC4, 0x19, 0x01, 0x00))
            assertThat(bytecode { +istore(256) }).containsExactly(*bytesOf(0xC4, 0x36, 0x01, 0x00))
            assertThat(bytecode { +lstore(256) }).containsExactly(*bytesOf(0xC4, 0x37, 0x01, 0x00))
            assertThat(bytecode { +fstore(256) }).containsExactly(*bytesOf(0xC4, 0x38, 0x01, 0x00))
            assertThat(bytecode { +astore(256) }).containsExactly(*bytesOf(0xC4, 0x3A, 0x01, 0x00))
        }

        @Test
        fun `takes the widest index a local slot can have`() {
            // max_locals is itself a u2, so this is as far as any of them go
            assertThat(bytecode { +iload(65535) }).containsExactly(*bytesOf(0xC4, 0x15, 0xFF, 0xFF))
            assertThat(bytecode { +astore(65535) }).containsExactly(*bytesOf(0xC4, 0x3A, 0xFF, 0xFF))
        }

        @Test
        fun `rejects an index past what a local slot can hold`() {
            assertThatIllegalArgumentException()
                .isThrownBy { bytecode { +iload(65536) } }
                .withMessageContaining("does not fit a u2")
            assertThatIllegalArgumentException().isThrownBy { bytecode { +fstore(65536) } }
        }
    }

    @Nested
    inner class Iinc {

        @Test
        fun `uses the compact form for small index and constant`() {
            assertThat(bytecode { +iinc(1, 2) }).containsExactly(*bytesOf(0x84, 0x01, 0x02))
            assertThat(bytecode { +iinc(255, 127) }).containsExactly(*bytesOf(0x84, 0xFF, 0x7F))
        }

        @Test
        fun `writes a negative constant in two's complement`() {
            assertThat(bytecode { +iinc(0, -1) }).containsExactly(*bytesOf(0x84, 0x00, 0xFF))
        }

        @Test
        fun `switches to the wide form for a large index`() {
            assertThat(bytecode { +iinc(256, 1) })
                .containsExactly(*bytesOf(0xC4, 0x84, 0x01, 0x00, 0x00, 0x01))
        }

        @Test
        fun `switches to the wide form for a large constant`() {
            assertThat(bytecode { +iinc(0, 128) })
                .containsExactly(*bytesOf(0xC4, 0x84, 0x00, 0x00, 0x00, 0x80))
            assertThat(bytecode { +iinc(0, -32768) })
                .containsExactly(*bytesOf(0xC4, 0x84, 0x00, 0x00, 0x80, 0x00))
        }

        @Test
        fun `rejects a constant beyond the wide form`() {
            assertThatIllegalArgumentException().isThrownBy { bytecode { +iinc(0, 32768) } }
            assertThatIllegalArgumentException().isThrownBy { bytecode { +iinc(0, -32769) } }
        }
    }

    @Nested
    inner class Jumps {

        @Test
        fun `writes the conditional opcodes`() {
            assertThat(bytecode { +goto }).startsWith(*bytesOf(0xA7))
            assertThat(bytecode { +ifeq }).startsWith(*bytesOf(0x99))
            assertThat(bytecode { +ifne }).startsWith(*bytesOf(0x9A))
            assertThat(bytecode { +ifge }).startsWith(*bytesOf(0x9C))
            assertThat(bytecode { +ifgt }).startsWith(*bytesOf(0x9D))
            assertThat(bytecode { +if_icmplt }).startsWith(*bytesOf(0xA1))
            assertThat(bytecode { +if_icmpge }).startsWith(*bytesOf(0xA2))
            assertThat(bytecode { +if_acmpeq }).startsWith(*bytesOf(0xA5))
            assertThat(bytecode { +if_acmpne }).startsWith(*bytesOf(0xA6))
            assertThat(bytecode { +ifnull }).startsWith(*bytesOf(0xC6))
            assertThat(bytecode { +ifnonnull }).startsWith(*bytesOf(0xC7))
        }

        @Test
        fun `writes a known destination as an offset from the opcode`() {
            // a jump to itself is the one destination that is an offset of zero
            assertThat(bytecode { val jump = +goto; link(jump, jump) })
                .containsExactly(*bytesOf(0xA7, 0x00, 0x00))
        }

        @Test
        fun `writes a backwards jump as a negative offset`() {
            // the opcode sits at 1, so jumping to 0 is an offset of -1
            assertThat(bytecode { val target = +nop; link(+goto, target) })
                .containsExactly(*bytesOf(0x00, 0xA7, 0xFF, 0xFF))
        }

        @Test
        fun `emits a placeholder when the destination is unknown`() {
            assertThat(bytecode { +goto }).containsExactly(*bytesOf(0xA7, 0x00, 0x00))
            assertThat(bytecode { +ifeq }).containsExactly(*bytesOf(0x99, 0x00, 0x00))
            assertThat(bytecode { +ifne }).containsExactly(*bytesOf(0x9A, 0x00, 0x00))
            assertThat(bytecode { +ifge }).containsExactly(*bytesOf(0x9C, 0x00, 0x00))
            assertThat(bytecode { +ifgt }).containsExactly(*bytesOf(0x9D, 0x00, 0x00))
            assertThat(bytecode { +if_icmplt }).containsExactly(*bytesOf(0xA1, 0x00, 0x00))
            assertThat(bytecode { +if_icmpge }).containsExactly(*bytesOf(0xA2, 0x00, 0x00))
            assertThat(bytecode { +ifnull }).containsExactly(*bytesOf(0xC6, 0x00, 0x00))
            assertThat(bytecode { +ifnonnull }).containsExactly(*bytesOf(0xC7, 0x00, 0x00))
        }

        @Test
        fun `patches the placeholder once the destination is known`() {
            // given
            val code = bytecode {
                val jump = +goto
                +nop
                link(jump, +nop)
            }

            // then the offset spans the jump instruction and the nop
            assertThat(code).containsExactly(*bytesOf(0xA7, 0x00, 0x04, 0x00, 0x00))
        }

        @Test
        fun `patches a conditional jump the same way`() {
            // given
            val code = bytecode {
                +iconst(0)
                val jump = +ifeq
                +`return`
                link(jump, +`return`)
            }

            // then the branch target is measured from the ifeq at offset 1
            assertThat(code).containsExactly(*bytesOf(0x03, 0x99, 0x00, 0x04, 0xB1, 0xB1))
        }

        @Test
        fun `lets several jumps share one target`() {
            // given
            val code = bytecode {
                val first = +goto
                val second = +goto
                val target = +nop
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
                val target = +nop
                val jump = +goto
                link(jump, target)
            }

            // then the jump at offset 1 reaches back to 0
            assertThat(code).containsExactly(*bytesOf(0x00, 0xA7, 0xFF, 0xFF))
        }

        @Test
        fun `leaves nothing to patch when the destination is given upfront`() {
            // given the destination handed to the jump itself rather than linked afterwards
            val code = bytecode {
                val target = +nop
                link(+goto, target)
            }

            // then
            assertThat(code).containsExactly(*bytesOf(0x00, 0xA7, 0xFF, 0xFF))
        }

        @Test
        fun `rejects a branch offset past a signed short`() {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { bytecode { val target = +nop; repeat(40_000) { +nop }; link(+goto, target) } }
                .withMessageContaining("does not fit an s2")
        }

        @Test
        fun `rejects a patched branch offset past a signed short`() {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { bytecode { val jump = +goto; repeat(40_000) { +nop }; link(jump, +nop) } }
                .withMessageContaining("does not fit an s2")
        }
    }

    /**
     * What each instruction does to the stack and the locals, which is all the analyzer knows
     * about it. Pops are listed in the order their operands were pushed, as JVMS lists them.
     */
    @Nested
    inner class Effects {

        @Test
        fun `push the type a constant carries`() {
            assertThat(aconst_null.stackEffects()).containsExactly(Push(NULL))
            assertThat(iconst(7).stackEffects()).containsExactly(Push(INTEGER))
            assertThat(lconst(1).stackEffects()).containsExactly(Push(LONG))
            assertThat(fconst(1).stackEffects()).containsExactly(Push(FLOAT))
            assertThat(ldc(DataPointer(3, ReferenceType("java/lang/String"))).stackEffects())
                .containsExactly(Push(ReferenceType("java/lang/String")))
        }

        @Test
        fun `take arithmetic operands of their own type`() {
            assertThat(iadd.stackEffects()).containsExactly(Pop(INTEGER), Pop(INTEGER), Push(INTEGER))
            assertThat(isub.stackEffects()).containsExactly(Pop(INTEGER), Pop(INTEGER), Push(INTEGER))
            assertThat(fadd.stackEffects()).containsExactly(Pop(FLOAT), Pop(FLOAT), Push(FLOAT))
            assertThat(fsub.stackEffects()).containsExactly(Pop(FLOAT), Pop(FLOAT), Push(FLOAT))
            assertThat(fmul.stackEffects()).containsExactly(Pop(FLOAT), Pop(FLOAT), Push(FLOAT))
            assertThat(fdiv.stackEffects()).containsExactly(Pop(FLOAT), Pop(FLOAT), Push(FLOAT))
            assertThat(fneg.stackEffects()).containsExactly(Pop(FLOAT), Push(FLOAT))
        }

        @Test
        fun `take the array and the index they read`() {
            assertThat(iaload.stackEffects())
                .containsExactly(Pop(ReferenceType("[I")), Pop(INTEGER), Push(INTEGER))
            assertThat(baload.stackEffects())
                .containsExactly(Pop(ReferenceType("[B")), Pop(INTEGER), Push(INTEGER))
        }

        @Test
        fun `take the array, the index and the value they write`() {
            assertThat(iastore.stackEffects())
                .containsExactly(Pop(ReferenceType("[I")), Pop(INTEGER), Pop(INTEGER))
            assertThat(bastore.stackEffects())
                .containsExactly(Pop(ReferenceType("[B")), Pop(INTEGER), Pop(INTEGER))
        }

        @Test
        fun `push an array of the element they are given`() {
            assertThat(newarray(ElementType.BYTE).stackEffects())
                .containsExactly(Pop(INTEGER), Push(ReferenceType("[B")))
            assertThat(newarray(ElementType.LONG).stackEffects())
                .containsExactly(Pop(INTEGER), Push(ReferenceType("[J")))
        }

        @Test
        fun `take the object and the value a field store writes`() {
            val owner = ReferenceType("A")

            assertThat(putfield(3, owner, LONG).stackEffects()).containsExactly(Pop(owner), Pop(LONG))
            assertThat(getfield(3, owner, LONG).stackEffects()).containsExactly(Pop(owner), Push(LONG))
            assertThat(getstatic(3, LONG).stackEffects()).containsExactly(Push(LONG))
        }

        @Test
        fun `take a call's receiver and arguments, then push what it returns`() {
            // given a receiver of class A and two arguments
            val args = listOf(ReferenceType("A"), INTEGER, FLOAT)

            // then every call shape takes the same operands in the same order
            val expected = arrayOf(Pop(ReferenceType("A")), Pop(INTEGER), Pop(FLOAT), Push(LONG))
            assertThat(invokevirtual(1, args, LONG).stackEffects()).containsExactly(*expected)
            assertThat(invokeinterface(1, args, LONG).stackEffects()).containsExactly(*expected)
            assertThat(invokespecial(1, args, LONG, null).stackEffects()).containsExactly(*expected)
            assertThat(invokestatic(1, args, LONG).stackEffects()).containsExactly(*expected)
        }

        @Test
        fun `push void for a call that returns nothing, which the analyzer leaves off the stack`() {
            assertThat(invokestatic(1, emptyList(), VOID).stackEffects()).containsExactly(Push(VOID))
        }

        @Test
        fun `take what a return or a branch tests`() {
            assertThat(ireturn.stackEffects()).containsExactly(Pop(INTEGER))
            assertThat(freturn.stackEffects()).containsExactly(Pop(FLOAT))
            assertThat(areturn.stackEffects()).containsExactly(Pop(AnyReference))
            assertThat(athrow.stackEffects()).containsExactly(Pop(AnyReference))
            assertThat(`return`.stackEffects()).isEmpty()
            assertThat(ifeq.stackEffects()).containsExactly(Pop(INTEGER))
            assertThat(if_icmplt.stackEffects()).containsExactly(Pop(INTEGER), Pop(INTEGER))
            assertThat(if_acmpeq.stackEffects()).containsExactly(Pop(AnyReference), Pop(AnyReference))
            assertThat(if_acmpne.stackEffects()).containsExactly(Pop(AnyReference), Pop(AnyReference))
            assertThat(ifnull.stackEffects()).containsExactly(Pop(AnyReference))
            assertThat(goto.stackEffects()).isEmpty()
        }

        @Test
        fun `take any reference to cast or test, and push what that says`() {
            assertThat(checkcast(4, "java/util/List").stackEffects())
                .containsExactly(Pop(AnyReference), Push(ReferenceType("java/util/List")))
            assertThat(instanceof(4).stackEffects()).containsExactly(Pop(AnyReference), Push(INTEGER))
        }

        @Test
        fun `read and write locals as their own type`() {
            assertThat(iload(2).localEffects()).containsExactly(Read(2, INTEGER))
            assertThat(fload(2).localEffects()).containsExactly(Read(2, FLOAT))
            assertThat(istore(1).localEffects()).containsExactly(Write(1, INTEGER))
            assertThat(fstore(1).localEffects()).containsExactly(Write(1, FLOAT))
            assertThat(lstore(1).localEffects()).containsExactly(Write(1, LONG))
            assertThat(lstore(1).stackEffects()).containsExactly(Pop(LONG))
            assertThat(iinc(3, 1).localEffects()).containsExactly(Read(3, INTEGER), Write(3, INTEGER))
        }

        @Test
        fun `touch no local unless they are a load, a store or iinc`() {
            assertThat(iadd.localEffects()).isEmpty()
            assertThat(iconst(1).localEffects()).isEmpty()
        }

        @Test
        fun `leave the effects of instructions that copy or move whatever is there to the frame`() {
            // dup and friends copy whatever type is on top, and aload, astore and new depend on
            // the frame too, so none of them has an effect it could state on its own
            listOf(dup, dup_x1, dup_x2, dup2, aload(0), astore(0), new(4)).forEach {
                assertThatIllegalStateException()
                    .isThrownBy { it.stackEffects() }
                    .withMessageContaining("takes its stack effect from the frame it runs in")
                assertThatIllegalStateException()
                    .isThrownBy { it.localEffects() }
                    .withMessageContaining("takes its local effect from the frame it runs in")
            }
        }
    }
}
