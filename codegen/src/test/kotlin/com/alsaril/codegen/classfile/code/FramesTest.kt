package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.classfile.attributes.AppendFrame
import com.alsaril.codegen.classfile.attributes.FullFrame
import com.alsaril.codegen.classfile.attributes.ObjectVariableInfo
import com.alsaril.codegen.classfile.attributes.SameFrame
import com.alsaril.codegen.classfile.attributes.SameFrameExtended
import com.alsaril.codegen.classfile.attributes.SameLocals1StackItemFrameExtended
import com.alsaril.codegen.classfile.attributes.SameLocals1StackItemFrameShort
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.FloatVariableInfo
import com.alsaril.codegen.classfile.attributes.SimpleVerificationTypeInfo.IntegerVariableInfo
import com.alsaril.codegen.constantpool.ConstantClassInfo
import com.alsaril.codegen.constantpool.ConstantUtf8Info
import com.alsaril.codegen.constantpool.UpdatableConstantPool
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FramesTest {

    @Test
    fun `records no frames for straight line code`() {
        assertThat(frames { iadd() }).isEmpty()
    }

    @Test
    fun `measures the first frame from the start of the method`() {
        assertThat(frames { frameSame(iload(0)) }).containsExactly(SameFrame(0))
        assertThat(frames { nop(); nop(); frameSame(iload(0)) }).containsExactly(SameFrame(2))
    }

    @Test
    fun `measures later frames from just past the previous one`() {
        // given three frames separated by one instruction each
        val recorded = frames {
            frameSame(iload(0))
            frameSame(iload(1))
            frameSame(iload(2))
        }

        // then the first is at 0, and each later delta skips the implicit +1
        assertThat(recorded).containsExactly(SameFrame(0), SameFrame(0), SameFrame(0))
    }

    @Test
    fun `counts every byte between two frames`() {
        // given
        val recorded = frames {
            frameSame(iload(0))
            repeat(4) { nop() }
            frameSame(iload(1))
        }

        // then the second frame sits at 5, one past the base of 1
        assertThat(recorded).containsExactly(SameFrame(0), SameFrame(4))
    }

    @Test
    fun `switches to the extended form for a distant frame`() {
        // given
        val recorded = frames {
            repeat(64) { nop() }
            frameSame(iload(0))
        }

        // then
        assertThat(recorded).containsExactly(SameFrameExtended(64))
    }

    @Test
    fun `records one frame when two land on the same offset`() {
        // two frames cannot share a bytecode offset, so the first one wins
        assertThat(frames {
            nop()
            val target = iload(0)
            frameSame(target)
            frameSame(target)
        }).containsExactly(SameFrame(1))
    }

    @Test
    fun `appends an integer local`() {
        assertThat(frames { frameAppend(iload(0), IntInfo) })
            .containsExactly(AppendFrame(0, listOf(IntegerVariableInfo)))
    }

    @Test
    fun `appends a float local`() {
        assertThat(frames { frameAppend(iload(0), FloatInfo) })
            .containsExactly(AppendFrame(0, listOf(FloatVariableInfo)))
    }

    @Test
    fun `describes a float on the stack`() {
        assertThat(frames { frameStack(iload(0), FloatInfo) })
            .containsExactly(SameLocals1StackItemFrameShort(0, FloatVariableInfo))
    }

    @Test
    fun `keeps the local types apart in one frame`() {
        assertThat(frames { frameFull(iload(0), listOf(IntInfo, FloatInfo, objInfo("[B")), listOf(FloatInfo)) })
            .containsExactly(
                FullFrame(
                    offsetDelta = 0,
                    locals = listOf(IntegerVariableInfo, FloatVariableInfo, ObjectVariableInfo(2)),
                    stack = listOf(FloatVariableInfo),
                ),
            )
    }

    @Test
    fun `appends an object local through a constant pool class entry`() {
        // given
        val cp = UpdatableConstantPool()

        // when
        val recorded = frames(cp) { frameAppend(iload(0), objInfo("[B")) }

        // then the descriptor is registered as a class and referenced by index
        assertThat(recorded).containsExactly(AppendFrame(0, listOf(ObjectVariableInfo(2))))
        assertThat(cp.build().entries).containsExactly(
            ConstantUtf8Info("[B"),
            ConstantClassInfo(nameIndex = 1),
        )
    }

    @Test
    fun `appends several locals in order`() {
        assertThat(frames { frameAppend(iload(0), objInfo("[B"), IntInfo) })
            .containsExactly(AppendFrame(0, listOf(ObjectVariableInfo(2), IntegerVariableInfo)))
    }

    @Test
    fun `advances the base past an append frame too`() {
        // given
        val recorded = frames {
            frameAppend(iload(0), IntInfo)
            repeat(2) { nop() }
            frameSame(iload(1))
        }

        // then
        assertThat(recorded).containsExactly(
            AppendFrame(0, listOf(IntegerVariableInfo)),
            SameFrame(2),
        )
    }

    @Test
    fun `describes a single stack item`() {
        // an exception handler starts with the throwable alone on the stack
        assertThat(frames { frameStack(iload(0), IntInfo) })
            .containsExactly(SameLocals1StackItemFrameShort(0, IntegerVariableInfo))
    }

    @Test
    fun `switches to the extended form for a distant stack frame`() {
        val recorded = frames {
            repeat(64) { nop() }
            frameStack(iload(0), IntInfo)
        }

        assertThat(recorded).containsExactly(SameLocals1StackItemFrameExtended(64, IntegerVariableInfo))
    }

    @Test
    fun `advances the base past a stack frame too`() {
        // given
        val recorded = frames {
            frameStack(iload(0), IntInfo)
            repeat(2) { nop() }
            frameSame(iload(1))
        }

        // then
        assertThat(recorded).containsExactly(
            SameLocals1StackItemFrameShort(0, IntegerVariableInfo),
            SameFrame(2),
        )
    }

    @Test
    fun `spells out both halves of a full frame`() {
        // given
        val recorded = frames { frameFull(iload(0), listOf(IntInfo, objInfo("[B")), listOf(IntInfo)) }

        // then
        assertThat(recorded).containsExactly(
            FullFrame(
                offsetDelta = 0,
                locals = listOf(IntegerVariableInfo, ObjectVariableInfo(2)),
                stack = listOf(IntegerVariableInfo),
            ),
        )
    }

    @Test
    fun `writes a full frame with nothing in it`() {
        assertThat(frames { frameFull(iload(0), emptyList(), emptyList()) })
            .containsExactly(FullFrame(0, emptyList(), emptyList()))
    }

    @Test
    fun `takes an object type from a class pointer as well as a name`() {
        // given
        val cp = UpdatableConstantPool()

        // when both spellings of the same class are used
        val recorded = frames(cp) { frameFull(iload(0), listOf(objInfo(self()), objInfo(THIS_CLASS)), emptyList()) }

        // then they land on the one pool entry
        assertThat(recorded).containsExactly(
            FullFrame(0, listOf(ObjectVariableInfo(2), ObjectVariableInfo(2)), emptyList()),
        )
        assertThat(cp.build().entries).containsExactly(
            ConstantUtf8Info(THIS_CLASS),
            ConstantClassInfo(nameIndex = 1),
        )
    }
}
