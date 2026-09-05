package com.alsaril.codegen.classfile.code

import com.alsaril.codegen.classfile.attributes.AppendFrame
import com.alsaril.codegen.classfile.attributes.ObjectVariableInfo
import com.alsaril.codegen.classfile.attributes.SameFrame
import com.alsaril.codegen.classfile.attributes.SameFrameExtended
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
        assertThat(frames { frameSame() }).containsExactly(SameFrame(0))
        assertThat(frames { nop(); nop(); frameSame() }).containsExactly(SameFrame(2))
    }

    @Test
    fun `measures later frames from just past the previous one`() {
        // given three frames separated by one nop each
        val recorded = frames {
            frameSame()
            nop()
            frameSame()
            nop()
            frameSame()
        }

        // then the first is at 0, and each later delta skips the implicit +1
        assertThat(recorded).containsExactly(SameFrame(0), SameFrame(0), SameFrame(0))
    }

    @Test
    fun `counts every byte between two frames`() {
        // given
        val recorded = frames {
            frameSame()
            repeat(5) { nop() }
            frameSame()
        }

        // then the second frame sits at 5, one past the base of 1
        assertThat(recorded).containsExactly(SameFrame(0), SameFrame(4))
    }

    @Test
    fun `switches to the extended form for a distant frame`() {
        // given
        val recorded = frames {
            repeat(64) { nop() }
            frameSame()
        }

        // then
        assertThat(recorded).containsExactly(SameFrameExtended(64))
    }

    @Test
    fun `appends an integer local`() {
        assertThat(frames { frameAppend(IntInfo) })
            .containsExactly(AppendFrame(0, listOf(IntegerVariableInfo)))
    }

    @Test
    fun `appends an object local through a constant pool class entry`() {
        // given
        val cp = UpdatableConstantPool()

        // when
        val recorded = builder(cp).apply { frameAppend(ObjInfo("[B")) }.build().second

        // then the descriptor is registered as a class and referenced by index
        assertThat(recorded).containsExactly(AppendFrame(0, listOf(ObjectVariableInfo(2))))
        assertThat(cp.build().entries).containsExactly(
            ConstantUtf8Info("[B"),
            ConstantClassInfo(nameIndex = 1),
        )
    }

    @Test
    fun `appends several locals in order`() {
        assertThat(frames { frameAppend(ObjInfo("[B"), IntInfo) })
            .containsExactly(AppendFrame(0, listOf(ObjectVariableInfo(2), IntegerVariableInfo)))
    }

    @Test
    fun `advances the base past an append frame too`() {
        // given
        val recorded = frames {
            frameAppend(IntInfo)
            repeat(3) { nop() }
            frameSame()
        }

        // then
        assertThat(recorded).containsExactly(
            AppendFrame(0, listOf(IntegerVariableInfo)),
            SameFrame(2),
        )
    }
}
