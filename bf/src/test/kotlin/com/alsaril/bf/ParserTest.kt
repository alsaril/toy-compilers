package com.alsaril.bf

import com.alsaril.bf.Command.DEC
import com.alsaril.bf.Command.IN
import com.alsaril.bf.Command.INC
import com.alsaril.bf.Command.LEFT
import com.alsaril.bf.Command.OUT
import com.alsaril.bf.Command.RIGHT
import com.alsaril.bf.Parser.parse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Folding a repeated command is invisible in a program's output, so it is checked
 * here on the instruction tree rather than end to end.
 */
class ParserTest {

    @Nested
    inner class Commands {

        @Test
        fun `produces nothing for an empty program`() {
            assertThat(parse("")).isEmpty()
        }

        @Test
        fun `maps every command character`() {
            assertThat(parse("<>+-.,")).containsExactly(
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
            assertThat(parse("+ hey you +")).containsExactly(CommandInstruction(INC, times = 2))
        }
    }

    @Nested
    inner class Folding {

        @Test
        fun `folds a repeated command into one instruction`() {
            assertThat(parse("+++")).containsExactly(CommandInstruction(INC, times = 3))
            assertThat(parse(">>")).containsExactly(CommandInstruction(RIGHT, times = 2))
        }

        @Test
        fun `keeps different commands apart`() {
            assertThat(parse("++--")).containsExactly(
                CommandInstruction(INC, times = 2),
                CommandInstruction(DEC, times = 2),
            )
        }

        @Test
        fun `never folds output, since each write has an effect`() {
            assertThat(parse("..")).containsExactly(
                CommandInstruction(OUT),
                CommandInstruction(OUT),
            )
        }

        @Test
        fun `never folds input, since each read consumes a byte`() {
            assertThat(parse(",,")).containsExactly(
                CommandInstruction(IN),
                CommandInstruction(IN),
            )
        }

        @Test
        fun `does not fold across a loop boundary`() {
            assertThat(parse("+[+]+")).containsExactly(
                CommandInstruction(INC),
                Loop(listOf(CommandInstruction(INC))),
                CommandInstruction(INC),
            )
        }

        @Test
        fun `resumes folding after a run is interrupted`() {
            assertThat(parse("++-+++")).containsExactly(
                CommandInstruction(INC, times = 2),
                CommandInstruction(DEC),
                CommandInstruction(INC, times = 3),
            )
        }

        @Test
        fun `folds a run of any length into one instruction`() {
            // the parser is not bounded by what a single opcode can encode; the
            // backend is what splits an oversized count into valid instructions
            assertThat(parse("+".repeat(100_000)))
                .containsExactly(CommandInstruction(INC, times = 100_000))
        }

        @Test
        fun `folds a long run of pointer moves too`() {
            assertThat(parse(">".repeat(70_000)))
                .containsExactly(CommandInstruction(RIGHT, times = 70_000))
        }
    }

    @Nested
    inner class Loops {

        @Test
        fun `records an empty loop`() {
            assertThat(parse("[]")).containsExactly(Loop(emptyList()))
        }

        @Test
        fun `nests a loop inside another`() {
            assertThat(parse("[[]]")).containsExactly(Loop(listOf(Loop(emptyList()))))
        }

        @Test
        fun `keeps sequential loops apart`() {
            assertThat(parse("[][]")).containsExactly(Loop(emptyList()), Loop(emptyList()))
        }

        @Test
        fun `keeps a loop that follows other instructions`() {
            assertThat(parse("+++[-]")).containsExactly(
                CommandInstruction(INC, times = 3),
                Loop(listOf(CommandInstruction(DEC))),
            )
        }

        @Test
        fun `keeps the body of a nested loop in order`() {
            assertThat(parse("[>[+]<]")).containsExactly(
                Loop(
                    listOf(
                        CommandInstruction(RIGHT),
                        Loop(listOf(CommandInstruction(INC))),
                        CommandInstruction(LEFT),
                    )
                )
            )
        }

        @Test
        fun `handles nesting far deeper than the stack would allow`() {
            // the whole point of parsing iteratively; assert on the size only, since
            // comparing a structure this deep would recurse through equals
            val depth = 10_000

            val instructions = parse("[".repeat(depth) + "+" + "]".repeat(depth))

            assertThat(instructions).hasSize(1)
        }
    }

    @Nested
    inner class Errors {

        @Test
        fun `rejects a stray closing bracket`() {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { parse("+]") }
                .withMessage("unexpected ']' at 1")
        }

        @Test
        fun `rejects a closing bracket that outnumbers the opening ones`() {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { parse("[]]") }
                .withMessage("unexpected ']' at 2")
        }

        @Test
        fun `rejects an unclosed bracket`() {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { parse("+[+") }
                .withMessage("']' expected at 3")
        }

        @Test
        fun `counts every character in the index, newlines included`() {
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { parse("+++\n++]") }
                .withMessage("unexpected ']' at 6")
        }
    }
}
