# bf

A Brainfuck compiler. Source goes in, a JVM class comes out, the JVM runs it — nothing is
interpreted. Built on [`codegen`](../codegen/README.md).

```bash
./gradlew :bf:run --args=program.bf
```

## Pipeline

```
source ──> Parser ──> List<Instruction> ──> ClassGenerator ──> class bytes ──> ExtendedRunnable
                       (folded tree)         + RunGenerator      ByteClassLoader
```

- **`Parser`** — hand-written, no parser generator.
- **`instructions.kt`** — `CommandInstruction(command, times)` and `Loop(body)`, a tree.
- **`ClassGenerator`** — the class shell: constructor, `guard`, the no-arg `run()`.
- **`RunGenerator`** — the program body, including all method splitting.

## Parser

Non-recursive: an explicit stack of instruction lists, pushed on `[` and popped on `]`.
Nesting depth is bounded by the heap rather than the call stack, so 10 000+ nested loops
parse fine. Unbalanced brackets fail loudly — `unexpected ']' at 6`, `']' expected at 12` —
rather than silently producing a different program.

**Instruction folding.** A run of the same command collapses into one instruction:
`+++++` becomes `CommandInstruction(INC, times = 5)`. `.` and `,` are never folded, since
each one has an effect. `times` is unbounded — keeping the encoding limits out of the IR
is the back end's job.

## Code generation

Also non-recursive: an explicit stack of `(instructions, emitted fragments)`, so deeply
nested programs generate without overflowing. Everything is emitted as `Fragment`s and
joined, which is what makes splitting possible.

**Method splitting.** A JVM method is capped at 65535 bytes of bytecode, and HotSpot
refuses to JIT-compile anything over **8000** (`HugeMethodLimit`) — so that is the budget.
Fragments are packed into chunks; a chunk that does not fit becomes its own `static`
method `fN`, and the parent emits a call. A loop body too big to inline is outlined the
same way, with loop control staying in the parent.

Budgets are **measured from the emitters rather than hardcoded**, so they follow any
change to the code they account for, and are computed once per compilation into
`Generation`:

```kotlin
bodyLengthLimit = methodLengthLimit - emitMethodPrefix().size - emitMethodPostfix().size
```

A fragment that outgrows a chunk on its own is given a method of its own instead of
stalling the packer, so splitting always makes progress.

## Runtime model

The generated class implements `ExtendedRunnable`:

```kotlin
interface ExtendedRunnable : Runnable {
    fun run(`in`: InputStream, `out`: OutputStream, memsize: Int, cycles: Int)
}
```

Streams and limits are parameters, which makes a program testable without touching
`System.in`/`System.out`. The inherited no-arg `run()` delegates with defaults.

State shared across the split methods:

| | |
|---|---|
| `byte[] tape` | the cells, `memsize` long |
| `int[2] state` | `[pointer, cycles]` |

**Bounds guard.** A generated `static guard(pointer, memsize)` runs before every cell
access and raises `Buffer overflow` if the pointer left the tape — deterministic instead
of relying on where the JVM happens to notice.

**Cycle counter.** `cycles` is decremented once per loop-body *entry* and raises
`Cycles overflow` at zero. Counting at body entry rather than at the condition means a
loop that is about to exit costs nothing, and a limit of *n* permits exactly *n*
iterations. It also makes non-terminating programs testable.

## Small back end details

- **Cell arithmetic** reduces to `times and 0xff` — a cell is a byte and `bastore` keeps
  the low eight bits, so nothing wider is observable.
- **Pointer moves** cannot reduce (every intermediate position matters for the guard), so
  a long move is split into `iinc` steps of at most `Short.MAX_VALUE`.
- **EOF reads as 0**, not `-1`. `read()` returning `-1` is clamped, which is what the
  usual `,[.,]` idiom needs to terminate. A real `0xFF` input byte stays `0xFF`.
- **Output is flushed** before returning, so a program without a trailing newline is not
  silently lost.

## Tests

68 tests. `ParserTest` covers folding, the loop tree and deep nesting; `ProgramTest` runs
compiled programs end to end and compares exact bytes — Hello World, a cat loop, cell
wrapping, bounds and cycle errors, and programs large enough to force splitting past both
the 8000 byte budget and the 65535 hard limit.
