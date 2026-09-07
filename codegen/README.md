# codegen

Writes JVM class files by hand — constant pool, methods, bytecode and stack map frames.
No ASM. Language-agnostic: it knows about the class file format, nothing about sources.

## Pipeline

```
ClassFileBuilder ──> ClassFile ──> ClassWriter ──> ByteArray ──> ByteClassLoader
      │                             (DosWriter)
      └── CodeBuilder (bytecode DSL) ──> Fragment
```

Everything serialisable implements `Writable`, which writes into a `ClassWriter` — a
narrow byte sink (`byte`, `short`, `int`, `long`, `double`, `bytes`, `utf8`). Class file
structures follow JVMS §4.1–4.7 one-to-one: `ClassFile`, `MethodInfo`, `AttributeInfo`
with `CodeAttribute` and `StackMapTableAttribute`.

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
method("f", "()I", maxStack = 2, maxLocals = 0, PUBLIC, STATIC) {
    iconst(2); iconst(3); iadd(); ireturn()
}
```

`@DslMarker` (`@CodeDsl`) keeps a nested block from resolving an outer receiver.

**Jumps are patched by closure.** A branch with no destination yet emits a placeholder
and returns a patcher, called once the target is known:

```kotlin
val jump = ifeq()
...
jump(loc())
```

## Stack map frames

Frames are recorded as offsets *relative to the previous frame*, which `CodeBuilder`
tracks so callers only say `frameSame()` or `frameAppend(...)`. It picks the compact or
extended encoding automatically, and drops a frame that lands on an offset the previous
one already covers — two frames may not share an offset, which happens naturally when a
loop body is empty. All four frame types are supported: `SameFrame`, `SameFrameExtended`,
`AppendFrame`, `FullFrame`.

## Two ways to define a method

**Regular** — emit straight into the method:

```kotlin
method(name, descriptor, maxStack, maxLocals, PUBLIC) { /* opcodes */ }
```

**From fragments** — build pieces independently and stitch them:

```kotlin
val prefix = emitFragment { ... }
val body   = emitFragment { ... }
method(name, descriptor, listOf(prefix, body).join(), maxStack, maxLocals, PUBLIC)
```

`Fragment` is code plus its frames plus its length. `List<Fragment>.join()` concatenates
them and **rewrites frame offsets** against the positions they land on in the joined code.
This is what lets a caller assemble a method out of parts, or split one body across
several methods.

Building a `CodeBuilder` *freezes* it: further emission throws, but **jump patchers still
work**, writing into the frozen array. That is what makes a fragment emitted early
patchable once a later fragment fixes its target.

## Loading

`ByteClassLoader.loadClass(name, bytes)` gives each class its own loader, so the same
class name can be defined repeatedly — one compilation per program, not per JVM.

## Width checks

A class file is mostly `u1` and `u2` fields, and a value that does not fit one is rejected
where it is written. There are only two ways a byte reaches the output, so there are only
two places that check:

- **`DosWriter.byte` / `short`** — the only `ClassWriter` implementation, so every
  structural field passes through it: pool indices and `constant_pool_count`, member and
  attribute counts, access flags, frame offset deltas, tags. All of them are unsigned, so
  the ranges are `0..0xff` and `0..0xffff`.
- **`CodeBuilder.b1` / `b2`** — bytecode never touches `ClassWriter`; it accumulates in the
  builder and leaves as a single `bytes(code)`. An operand here may be unsigned (an opcode,
  a local index, a pool index) or signed (an `iinc` delta, a branch offset), so each range
  is the union of the two.

Two constraints neither place can express get their own check. A branch offset must fit a
*signed* short, which the union above is too wide to catch, so `Jumps` narrows it where
the offset is computed. And `code_length` is a `u4` on the wire that the JVMS caps at
65535, so `CodeAttribute` checks it in its `init`.

Each check raises an `IllegalArgumentException` naming the value and the width it did not
fit, at the point the value is emitted.

## Tests

Byte-level expectations are written out literally from the JVMS rather than produced by a
second copy of the encoder, and `GeneratedClassTest` loads and runs generated classes so
the **JVM verifier** checks the pool, bytecode and frames.
