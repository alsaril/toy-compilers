# codegen

Writes JVM class files by hand — constant pool, fields, methods, bytecode, stack map frames
and exception handlers. No ASM. Language-agnostic: it knows about the class file format,
nothing about sources.

## Pipeline

```
ClassFileBuilder ─> CodeBuilder ─> Fragment ─> BytecodeSerializer ─> ClassFile ─> ClassWriter ─> ByteArray
```

Five packages, and the dependencies run one way — `code` on top, `constantpool` at the
bottom, no cycles:

| package | holds |
|---|---|
| `code` | `ClassFileBuilder`, `CodeBuilder`, `Fragment`, `BytecodeSerializer`, `Members`, `Pointers`, `Frames` — everything that builds |
| `instruction` | the opcodes, their encodings and their stack effects |
| `classfile` | the static JVMS records: `ClassFile`, `FieldInfo`, `MethodInfo`, `AccessFlag`, descriptors, and `attributes/` |
| `constantpool` | the pool, and the typed indices into it (`ClassPointer`, `MethodDescriptor`, `FieldDescriptor`, `DataPointer`) |
| *(root)* | `ClassWriter`, `Writable`, `ByteClassLoader`, `Compiler` |

A body is built as a **list of instructions**, not as bytes. Nothing has an address until
`BytecodeSerializer` lays the fragment out, and that is also when `max_stack`, `max_locals`,
the frame deltas and the exception table offsets are derived. A caller never names an
offset.

Everything serialisable implements `Writable`, which writes into a `ClassWriter` — a narrow
byte sink implemented once, by `DosWriter` (`u1`, `s1`, `u2`, `s2`, `int`, `float`, `long`,
`double`, `bytes`, `utf8`). Class file structures follow JVMS §4.1–4.7 one-to-one:
`ClassFile`, `FieldInfo`, `MethodInfo`, `AttributeInfo` with `CodeAttribute` and
`StackMapTableAttribute`, and `ExceptionHandler` for a row of the `Code` attribute's exception table.

## Constant pool

`UpdatableConstantPool` collects entries while code is generated, then freezes into a
`StaticConstantPool`. Every kind is **cached and deduplicated** — utf8, int, long, double,
class, string, name-and-type, and field/method/interface refs — so repeating a name or a
descriptor costs one entry. Long and double correctly occupy two slots.

`ClassPointer` / `DataPointer` resolve to a pool index at creation, so `clazz("A")` or
`string("boom")` registers once and the index is fixed from then on.

## Instructions

`instruction/` holds the IR, and each opcode is one line stating the two
things about it that matter:

```kotlin
data object aconst_null : NoArgInstruction(0x01), PushesOne
data class iload(override val index: Int) : LocalSlotInstruction(0x1a, 0x15), PushesOne
```

**A class is named after its JVMS mnemonic, in lower case**, so a body reads as the
bytecode it is rather than as a translation of it. `return` is spelled `` `return` ``,
being a keyword.

- **`Encodings.kt`** — how an instruction and its operands reach the stream, and the width
  check for the operand each shape writes. Some instructions go one further and pick the
  opcode themselves from the operand, so the choice is never frozen into a type and
  re-slotting or re-valuing one re-encodes it: `LocalSlotInstruction` takes the compact
  form for slots 0–3, the operand form to 255 and the wide prefix past that; `iconst`
  spans `iconst_<i>`, `bipush` and `sipush`; `ldc` and `iinc` each pick between a narrow
  and a wide form. `iconst`, `ldc` and `iinc` carry their range check in `init`;
  `LocalSlotInstruction` checks its slot as it writes, its `index` being abstract and so
  out of reach of the base class's `init`.
- **`Effects.kt`** — what an instruction does to the operand stack and to the local slots.
  Eight mixins (`PushesOne`, `PopsOne`, `PopsTwoPushesOne`, `Invocation`, `TouchesLocal`, …)
  supply the effect for every opcode but seven: the four `dup` forms (`dup`, `dup_x1`,
  `dup_x2`, `dup2`), each one of a kind, and the three field accesses — `getstatic`,
  `getfield`, `putfield` — whose depth comes from the field's type rather than from the
  opcode.
- **`Instruction.kt`** — the opcodes, and the sealed interface both axes refine.

`Instruction` is sealed, so all three files must stay in that one package — nothing outside
it can introduce an instruction.

## Bytecode DSL

An instruction is emitted by naming it after a `+`. That operator, declared on
`CodeBuilder`, is the **whole** opcode surface:

```kotlin
operator fun Instruction.unaryPlus(): Label = add(this)
```

```kotlin
method("f", "()I", PUBLIC, STATIC) {
    +iconst(2); +iconst(3); +iadd; +ireturn
}
```

Being a member rather than a top-level extension, it is in scope only inside a
`CodeBuilder.() -> Unit` block. Around it sits the code that does more than name an
opcode: `Members` (pool refs for methods and fields), `Pointers` (refs for classes and
constants), `Frames`, and `link` / `` `catch` `` / `end` for addressing.

`@DslMarker` (`@CodeDsl`) keeps a nested block from resolving an outer receiver.

**`+` hands back a `Label`** — the position of the instruction it added,
as an index into the instructions of the builder that handed it out. A label belongs to
that builder and nowhere else: one handed to a different builder is refused rather than
silently naming whatever sits at that index there. That single return value is the whole
addressing story, since jump targets, frame anchors and exception ranges are all labels.

Indices are also what makes a fragment portable. A jump, a frame or a guarded range inside
one refers to positions in its own instruction list, so splicing it somewhere else shifts
every reference by the number of instructions ahead of it and nothing else has to change —
where a byte offset would have had to be recomputed, and a jump patched.

**Int and float are both covered** — constants, locals, arithmetic, negation and returns —
alongside `areturn`, `checkcast`, `instanceof`, `newarray`, `getstatic`, `getfield`,
`putfield` and the invoke family. A float
constant that has an opcode of its own (0, 1, 2) uses it; anything else goes to the pool as
`ldc`. `newarray` takes the `PrimitiveType` of its element and encodes the atype code itself.

**Local slots widen automatically.** `iload`, `fload`, `aload` and the stores take a slot
and nothing else; which of the three encodings that slot needs is settled when the
instruction is written, as above. The ceiling is 65535, which is `max_locals`' own limit,
and the [width checks](#width-checks) reject anything beyond.

**Jumps name a label.** A branch is emitted like anything else and linked to its target
whenever that becomes known, before or after; the operand is a placeholder until layout
resolves it:

```kotlin
val jump = +ifeq
...
val target = +iload(1)
link(jump, target)
```

When the target already exists, the two collapse into one line — `link(+goto, head)`.

## Calls and fields

The three method helpers differ only in how many slots a call takes off the stack, which is
what `max_stack` is derived from:

| helper | for | operands |
|---|---|---|
| `method` | `invokevirtual`, `invokespecial` | arguments **plus the receiver** |
| `smethod` | `invokestatic` | arguments alone |
| `imethod` | `invokeinterface` | arguments plus the receiver, and the count byte |

Each hands back a descriptor that the instruction takes whole, so the slot counts reach
`max_stack` without being restated:

```kotlin
+invokestatic(smethod(self(), "f", "(I)I"))
```

`field` carries the slots its type occupies, so a `long` or a `double` counts as two in each
of the field accesses: `getstatic` pushes them, `getfield` swaps the receiver for them, and
`putfield` takes them off together with the receiver.

`constructDefault(clazz(...))` is the one helper that emits rather than just resolves: `new`,
`dup` and `invokespecial` of the class's `<init>()V`, leaving the fresh instance on the stack.

Picking the wrong one is not a compile error and usually not a crash — it shifts the derived
depth by one, which over-declares (harmless) or under-declares (`VerifyError`). Hence the
separate names rather than a boolean.

The `field` above only *refers* to a field, of this class or any other. A field this class
owns is declared on `ClassFileBuilder`, next to its methods, with the same `AccessFlag`s:

```kotlin
classFile("Holder", "java/lang/Object")
    .field("value", "Ljava/lang/Object;", PRIVATE, FINAL)
    .method("get", "()Ljava/lang/Object;", PUBLIC) {
        +aload(0)
        +getfield(field(self(), "value", "Ljava/lang/Object;"))
        +areturn
    }
```

Fields are written in the order they were declared, with no attributes — so no
`ConstantValue`; a field starts at its default and is set by code.

## Stack map frames

A frame is attached to the instruction it describes, under that instruction's label:

```kotlin
val target = +iload(1)
link(jump, target)
frameAppend(target, IntInfo)
```

`frameSame`, `frameStack`, `frameAppend` and `frameFull` are the four kinds. A frame is
written as the distance from the *previous* frame, so the delta only exists once the code
is laid out — until then the frame carries its instruction index and the serializer sorts,
measures and picks the compact or extended encoding.

An offset can be named only once. Two requests for the **same** frame on one instruction
collapse; two that **disagree** are refused, naming the offset and both descriptions.

Almost all of the frame kinds in JVMS §4.7.4 are implemented; only `chop_frame` is missing.

## Exception handlers

A handler is a row of the `Code` attribute's exception table: the half-open range it covers,
where to send a throw, and the type it catches. All three are labels, recorded in one call:

```kotlin
val guarded = +baload
val done = +goto

val caught = +astore(2)
`catch`(guarded, to = done, handler = caught, type = clazz("java/lang/ArrayIndexOutOfBoundsException"))
```

`end_pc` is exclusive, so a range running to the end of the code names one past the last
instruction. No instruction is there, so `end()` names it — the one label that resolves to
`code_length` rather than to an offset, and the one thing it is good for:

```kotlin
`catch`(guarded, to = end(), handler = caught, type = null)
```

A `null` type is `catch_type` 0, which is how JVMS spells "any throwable" and what a
`finally` needs. `ExceptionHandler` refuses a range covering no instruction in its `init` —
the jvm rejects `start_pc == end_pc` at load time, and it is a relation between two fields
rather than the width of either, so the writer cannot catch it.

## Three ways to define a method

**Regular** — emit straight into the method:

```kotlin
method(name, descriptor, PUBLIC) { /* opcodes */ }
```

**From fragments** — build pieces independently and stitch them:

```kotlin
val prefix = emitFragment { ... }
val body   = emitFragment { ... }
method(name, descriptor, listOf(prefix, body).join(), PUBLIC)
```

**Spliced** — paste a fragment into a method as it is being emitted:

```kotlin
method(name, descriptor, PUBLIC) {
    fragment(prefix); /* opcodes */; fragment(body)
}
```

`newCodeBuilder()` hands out builders sharing the class's constant pool, so pieces can be
built apart and spliced into a method of the same class.

`Fragment` is an instruction list plus its jumps, frames, handlers and encoded length.
Joining and splicing **shift every index by the instruction count ahead of it** — not by the
byte length, which is the same number only while every instruction is one byte wide. There
is no offset arithmetic and nothing to patch afterwards: a fragment's jumps and frames point
at positions in its own list, and moving the list moves them.

`build()` reads the instructions out into a fragment rather than freezing the builder, so it
can be called more than once and emission can carry on afterwards.

## Method limits

**Both limits are derived; neither is given.** `BytecodeSerializer.analyze` walks the
instruction list breadth-first from the method entry and from every handler entry, carrying
the stack depth:

- `max_stack` is the deepest the walk sees. Each instruction declares what it pops and
  pushes via its effect mixin, so the depth is computed, never declared.
- `max_locals` is the highest slot any instruction touches, plus one, against the floor the
  descriptor sets — a `long` or a `double` argument takes two slots, and an instance method
  needs one more for `this`.

A **handler entry is a root of its own**, entered with the throwable alone on the stack, so
a handler body that nothing falls into is still walked.

The walk is also where a malformed body is caught, each message naming the instruction
and its index:

```
a method body must hold at least one instruction
ifeq at 1 was never linked to a target
nop at 0 continues to 1, which is past the last instruction
ireturn at 3 is reached with a stack 1 deep on one path and 0 deep on another
iadd at 0 pops 2 from a stack 0 deep
instruction 1 is unreachable
```

## Loading

`ByteClassLoader.loadClass(name, bytes)` gives each class its own loader, so the same class
name can be defined repeatedly — one compilation per program, not per JVM.

`Compiler.pipeline` is the whole contract between a language module and this one:

```kotlin
pipeline(source, parse, generate, Program::class.java)
```

Parse the source into whatever IR the front end likes, generate class bytes from it, load
them, check the class implements the interface the front end declared, and instantiate it
through its no-argument constructor. Anything the program needs at run time is passed to
the interface's method, not to the constructor.
A front end is three lines on top of it — everything language-specific is the two functions
passed in.

## Width checks

A class file is mostly `u1` and `u2` fields, and a value that does not fit one is rejected
where it is written. There are two layers:

- **`DosWriter`** — the only `ClassWriter` implementation, so every byte of the file passes
  through it: pool indices and counts, access flags, frame deltas, exception table
  locations, tags, and every instruction operand. Signedness is per call, because operands
  are not all one shape:

  ```kotlin
  u1(0x10)   // bipush, an opcode
  s1(value)  // its operand, a signed byte
  ```

- **The encoding shapes** — each `Encodings.kt` base class checks its operand in `init`, so
  a bad value is refused when the instruction is *constructed* rather than when the body is
  finally written. The four that write their operands directly — `iconst`, `ldc`, `iinc`
  and `invokeinterface` — carry the same check themselves.

`BytecodeSerializer.s2At` is the same check for a branch offset, which only exists once the
target's position is known.

One constraint neither layer can express gets its own check: `code_length` is a `u4` on the
wire that the JVMS caps at `1..65535` — a method may be neither empty nor larger — so
`CodeAttribute` checks both ends in its `init`.

Each check raises an `IllegalArgumentException` naming the value and the width it did not
fit, at the point the value is emitted.

## Tests

Byte-level expectations are written out literally from the JVMS rather than produced by a
second copy of the encoder, and `GeneratedClassTest` loads and runs generated classes so
the **JVM verifier** checks the pool, bytecode, frames and exception table.
