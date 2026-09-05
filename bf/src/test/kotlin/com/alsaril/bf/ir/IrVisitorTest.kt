package com.alsaril.bf.ir

import com.alsaril.bf.BFLexer
import com.alsaril.bf.BFParser
import com.alsaril.bf.ir.Command.*
import org.antlr.v4.runtime.CharStreams.fromString
import org.antlr.v4.runtime.CommonTokenStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class IrVisitorTest {

    private fun ir(source: String): List<IrInstruction> {
        val parser = BFParser(CommonTokenStream(BFLexer(fromString(source))))
        return IrVisitor().apply { visit(parser.expr()) }.instructions()
    }

    @Nested
    inner class Commands {

        @Test
        fun `produces nothing for an empty program`() {
            assertThat(ir("")).isEmpty()
        }

        @Test
        fun `maps every command character`() {
            assertThat(ir("<>+-.,")).containsExactly(
                CommandInstruction(LEFT),
                CommandInstruction(RIGHT),
                CommandInstruction(INC),
                CommandInstruction(DEC),
                CommandInstruction(OUT),
                CommandInstruction(IN),
            )
        }

        @Test
        fun `skips characters that are not commands`() {
            assertThat(ir("+ hey you +")).containsExactly(CommandInstruction(INC, times = 2))
        }
    }

    @Nested
    inner class Folding {

        @Test
        fun `folds a repeated command into one instruction`() {
            assertThat(ir("+++")).containsExactly(CommandInstruction(INC, times = 3))
            assertThat(ir(">>")).containsExactly(CommandInstruction(RIGHT, times = 2))
        }

        @Test
        fun `keeps different commands apart`() {
            assertThat(ir("++--")).containsExactly(
                CommandInstruction(INC, times = 2),
                CommandInstruction(DEC, times = 2),
            )
        }

        @Test
        fun `never folds output, since each write has an effect`() {
            assertThat(ir("..")).containsExactly(
                CommandInstruction(OUT),
                CommandInstruction(OUT),
            )
        }

        @Test
        fun `never folds input, since each read consumes a byte`() {
            assertThat(ir(",,")).containsExactly(
                CommandInstruction(IN),
                CommandInstruction(IN),
            )
        }

        @Test
        fun `does not fold across a loop boundary`() {
            assertThat(ir("+[+]+")).containsExactly(
                CommandInstruction(INC),
                LoopBegin,
                CommandInstruction(INC),
                LoopEnd(startIndex = 1),
                CommandInstruction(INC),
            )
        }

        @Test
        fun `resumes folding after a run is interrupted`() {
            assertThat(ir("++-+++")).containsExactly(
                CommandInstruction(INC, times = 2),
                CommandInstruction(DEC),
                CommandInstruction(INC, times = 3),
            )
        }
    }

    @Nested
    inner class Loops {

        @Test
        fun `records an empty loop`() {
            assertThat(ir("[]")).containsExactly(LoopBegin, LoopEnd(startIndex = 0))
        }

        @Test
        fun `points each loop end at its own beginning when nested`() {
            assertThat(ir("[[]]")).containsExactly(
                LoopBegin,
                LoopBegin,
                LoopEnd(startIndex = 1),
                LoopEnd(startIndex = 0),
            )
        }

        @Test
        fun `points each loop end at its own beginning when sequential`() {
            assertThat(ir("[][]")).containsExactly(
                LoopBegin,
                LoopEnd(startIndex = 0),
                LoopBegin,
                LoopEnd(startIndex = 2),
            )
        }

        @Test
        fun `indexes a loop that follows other instructions`() {
            assertThat(ir("+++[-]")).containsExactly(
                CommandInstruction(INC, times = 3),
                LoopBegin,
                CommandInstruction(DEC),
                LoopEnd(startIndex = 1),
            )
        }

        @Test
        fun `keeps the body of a nested loop in order`() {
            assertThat(ir("[>[+]<]")).containsExactly(
                LoopBegin,
                CommandInstruction(RIGHT),
                LoopBegin,
                CommandInstruction(INC),
                LoopEnd(startIndex = 2),
                CommandInstruction(LEFT),
                LoopEnd(startIndex = 0),
            )
        }
    }
}
