# codegen

Writes JVM class files by hand — constant pool, methods, bytecode, stack map frames and
exception handlers. No ASM. Language-agnostic: it knows about the class file format,
nothing about sources.

## Pipeline

```
ClassFileBuilder ──> CodeBuilder ──> Fragment ──> ClassFile ──> ClassWriter ──> ByteArray
```

Everything serialisable implements `Writable`, which writes into a `ClassWriter` — a
narrow byte sink implemented once, by `DosWriter` (`byte`, `short`, `int`, `long`,
`double`, `bytes`, `utf8`). Class file structures follow JVMS §4.1–4.7 one-to-one:
`ClassFile`, `MethodInfo`, `AttributeInfo` with `CodeAttribute` and
`StackMapTableAttribute`, and `ExceptionHandler` for a row of the
`Code` attribute's exception table.

## Constant pool

`UpdatableConstantPool` collects entries while code is generated, then freezes into a
`StaticConstantPool`. Every kind is **cached and deduplicated** — utf8, int, long, double,
class, string, name-and-type, and field/method/interface refs — so repeating a name or a
descriptor costs one entry. Long and double correctly occupy two slots.

`ClassPointer` / `DataPointer` resolve to a pool index at creation, so `clazz("A")` or
`string("boom")` registers once and the index is fixed from then on.

## Bytecode DSL

`CodeBuilder` holds the byte sink and the frame bookkeeping. The instruction set lives
around it as extension functions, split by concern — `Opcodes`, `Jumps`, `Invocations`,
`Frames`, `Pointers` — so it stays usable inside a `CodeBuilder.() -> Unit` block:

```kotlin
method("f", "()I", maxStack = 2, PUBLIC, STATIC) {
    iconst(2); iconst(3); iadd(); ireturn()
}
```

`@DslMarker` (`@CodeDsl`) keeps a nested block from resolving an outer receiver.

**Int and float are both covered** — constants, locals, arithmetic, negation and returns —
alongside `checkcast`, `instanceof`, `newarray` and the invoke family. A float constant
that has an opcode of its own (0, 1, 2) uses it; anything else goes to the pool as `ldc`.
`newarray` takes the `PrimitiveType` of its element and encodes the atype code itself.

**Local slots widen automatically.** `iload`, `fload`, `aload` and the stores pick the
compact opcode for slots 0–3, the one-operand form up to 255, and the `wide` prefix past
that — a caller names a slot, never an encoding. The ceiling is 65535, which is
`max_locals`' own limit, and the [width checks](#width-checks) reject anything beyond.

**Jumps are patched by closure.** A branch with no destination yet emits a placeholder
and returns a patcher, called once the target is known:

```kotlin
val jump = ifeq()
...
jump(loc())
```

## Stack map frames

Frames are recorded as offsets *relative to the previous frame*, which `CodeBuilder`
tracks so callers only say `frameSame()`, `frameAppend(...)`, `frameStack(...)` or
`frameFull(...)`. It picks the compact or extended encoding automatically, and drops a
frame that lands on an offset the previous one already covers — two frames may not share
an offset, which happens naturally when a loop body is empty.

Almost all of the frame kinds in JVMS §4.7.4 are implemented; only `chop_frame` is
missing.

## Exception handlers

A handler is a row of the `Code` attribute's exception table: the range it covers, where
to jump, and the type it catches. `try` marks the start of the range and `catch` closes
it, returning a patcher that records the handler once its location is known — the same
closure trick as a jump. A `catch` with no type matches any throwable.

**A patcher records a row rather than overwriting one**, which is the one way it differs
from a jump's. Patching a jump twice retargets it, so the second call is as good as the
first; patching a handler twice would append a second row and leave the first behind,
dead, since the jvm takes the earliest match. So a handler patcher is single-use and
says so:

```
this catch has already been given a handler
```

`try` itself records nothing at all — it is `TryPointer(loc())`, an offset and no more —
so a range that is opened and never closed emits nothing and costs nothing. That is
unlike an unpatched jump, which leaves a live `goto +0` behind, and is why only one of
the two is counted.

## Three ways to define a method

**Regular** — emit straight into the method:

```kotlin
method(name, descriptor, maxStack, PUBLIC) { /* opcodes */ }
```

**From fragments** — build pieces independently and stitch them:

```kotlin
val prefix = emitFragment { ... }
val body   = emitFragment { ... }
method(name, descriptor, listOf(prefix, body).join(), maxStack, PUBLIC)
```

**Spliced** — paste a fragment into a method as it is being emitted:

```kotlin
method(name, descriptor, maxStack, PUBLIC) {
    fragment(prefix); /* opcodes */; fragment(body)
}
```

`newCodeBuilder()` hands out builders sharing the class's constant pool, so pieces can be
built apart and spliced into a method of the same class.

`Fragment` is code plus its frames, its exception handlers, its length and the stack and
locals it needs. Joining and splicing both **rewrite the positions inside a fragment**
against where it lands — relative frame offsets and absolute handler locations alike —
through one `Fragment.recordAt`, so there is a single place to get that arithmetic wrong.
This is what lets a caller assemble a method out of parts, or split one body across several
methods.

Building a `CodeBuilder` *freezes* it: further emission throws, but **jump patchers still
work**, writing into the frozen array — the very array the fragment holds. That is what
makes a fragment emitted early patchable once a later fragment fixes its target.

**Which is why joining and splicing are not interchangeable: joining moves blocks,
splicing copies bytes.** `join()` reuses the arrays its fragments hold, so a jump patched
*after* the join still lands, as does `bytecode()` on a single unsplit block. `fragment()`
copies the bytes into the builder, so it cannot — the patch would reach the fragment it was
taken from and never the copy. Rather than lose it quietly, each fragment carries a live
count of the patches still waiting, and the copying paths refuse one that still owes.

**The two kinds are counted apart, because they do not survive the same operations.** A
jump patch writes into the byte array a fragment holds; a handler patch appends a row to
the list it holds. So joining, which shares the arrays but rewrites every row against
where its fragment landed, is safe for one and not the other — and flattening, which
copies the arrays but never reads the rows, is the other way about. Splicing copies both:

| | jump | handler |
|---|---|---|
| `join()` | shares the block, patch still lands | rewrites the row, **refused** |
| `bytecode()` over several blocks | copies the bytes, **refused** | rows untouched |
| `fragment()` | copies the bytes, **refused** | copies the row, **refused** |

```
splicing copies the bytes of a fragment ... patch before splicing
splicing copies the handler rows of a fragment ... patch before splicing
flattening several blocks copies them ... patch before flattening
joining rewrites the handler rows of a fragment ... patch before joining
```

A fragment patched and then left alone is the case all of this protects: a handler
recorded after its builder was frozen still reaches the fragment that builder produced,
the same way a late jump does.

## Method limits

**`max_locals` is derived, never given.** The descriptor says how many slots the arguments
occupy — a `long` or a `double` takes two, everything else one — and an instance method
needs one more for `this`. The body may reach further, so every `iload`, `fload`, `astore`
and the rest records the slot it touched, and the method takes whichever is larger:

```kotlin
max(args.sumOf { it.slots } + (if (static) 0 else 1), fragment.maxLocals)
```

It is a count of slots rather than a highest index, so one local at index 0 means 1.
Descriptors are parsed by `parseFunctionDescriptor` in `descriptor.kt`, which is also where
`newarray` gets the element type it encodes.

**`max_stack` is given but never lowered.** `maxStack` on `method` is a floor, and a body
or a spliced fragment that needs more raises it — so a caller can pass `0` and let the code
speak for itself, or state a number and have it honoured if the body stays inside it.

**Both are counts, raised and never lowered**, which is what lets a spliced fragment carry
its own figures across unchanged:

```kotlin
maxStack(fragment.maxStack)
maxLocals(fragment.maxLocals)
```

An instruction has a slot, not a count, so the conversion happens in one place rather than
at each of the seven call sites that need it:

```kotlin
internal fun local(slot: Int, slots: Int = 1) = maxLocals(slot + slots)
```

`slots` is what makes a `long` or a `double` reserve the pair it occupies. Keeping the two
shapes apart matters: feeding a count to something expecting a slot over-declares by one,
and feeding a slot to something expecting a count under-declares — and only the second is
rejected, so the first is the kind of mistake that survives a test suite.

## Loading

`ByteClassLoader.loadClass(name, bytes)` gives each class its own loader, so the same
class name can be defined repeatedly — one compilation per program, not per JVM.

`Compiler.pipeline` is the whole contract between a language module and this one:

```kotlin
pipeline(source, parse, generate, Program::class.java)
```

Parse the source into whatever IR the front end likes, generate class bytes from it, load
them, check the class implements the interface the front end declared, and instantiate it.
Both `bf` and `math` are three lines on top of it — everything language-specific is the two
functions passed in.

## Width checks

A class file is mostly `u1` and `u2` fields, and a value that does not fit one is rejected
where it is written. There are only two ways a byte reaches the output, so there are only
two places that check:

- **`DosWriter.byte` / `short`** — the only `ClassWriter` implementation, so every
  structural field passes through it: pool indices and `constant_pool_count`, member and
  attribute counts, access flags, frame offset deltas, exception table locations, tags.
  All of them are unsigned, so the ranges are `0..0xff` and `0..0xffff`.
- **`CodeBuilder.u1` / `s1` / `u2` / `s2`** — bytecode never touches `ClassWriter`; it
  accumulates in the builder and leaves as a single `bytes(code)`. Operands here are not
  all one shape, so there is an emitter per width and signedness and each call names the
  field it fills:

```kotlin
u1(0x10)   // bipush, an opcode
s1(value)  // its operand, a signed byte
```

`s2At` is the same check for a branch offset patched in after the fact, once its target
is known.

Two constraints neither place can express get their own check:

- `code_length` is a `u4` on the wire that the JVMS caps at `1..65535` — a method may be
  neither empty nor larger — so `CodeAttribute` checks both ends in its `init`.
- An exception table row must cover at least one instruction. Each of its four locations
  fits a `u2` on its own, so the writer sees nothing wrong with `start_pc == end_pc`; the
  jvm refuses it at load time with `ClassFormatError: Illegal exception table range`. It
  is a relation between two fields rather than the width of either, so `ExceptionHandler`
  checks it in its `init` — the one place every row passes through, hand-built or
  patched.

Each check raises an `IllegalArgumentException` naming the value and the width it did not
fit, at the point the value is emitted.

## Tests

Byte-level expectations are written out literally from the JVMS rather than produced by a
second copy of the encoder, and `GeneratedClassTest` loads and runs generated classes so
the **JVM verifier** checks the pool, bytecode, frames and exception table.
