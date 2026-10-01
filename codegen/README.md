# codegen

Writes JVM class files by hand — constant pool, fields, methods, bytecode, stack map frames
and exception handlers. No ASM. Language-agnostic: it knows about the class file format,
nothing about sources. Classes are written as version 65, the format of Java 21.

A method body is given as instructions alone. Everything the class file needs to know about
the code on top of that — `max_stack`, `max_locals` and the stack map frames — is derived by
an analysis that checks the body the way the JVM's verifier does, tracking the type of every
value on the stack and in every local, and refuses a body the verifier would.

## Pipeline

```
ClassFileBuilder ─> CodeBuilder ─> Fragment ─> BytecodeSerializer ─> ClassFile ─> ClassWriter ─> ByteArray
                                                       │
                                                    Analyzer
```

Seven packages, and the dependencies run one way — `code` on top, then `assembly`, then
`instruction`, `classfile` and `constantpool`, with `verification` at the bottom; no cycles:

| package | holds |
|---|---|
| `code` | `ClassFileBuilder`, `CodeBuilder`, `BootstrapMethods`, `Members`, `Pointers`, `Constants` — everything that builds |
| `assembly` | `BytecodeSerializer`, `Analyzer`, `Locals`, `ClassHierarchy` — what turns a built body into a `Code` attribute: the [analysis](#analysis) and the layout |
| `instruction` | the opcodes, their encodings, their effects on the stack and the locals, and `Fragment`, a body as a list of them |
| `classfile` | the static JVMS records: `ClassFile`, `FieldInfo`, `MethodInfo`, `AccessFlag`, descriptors, and `attributes/` |
| `constantpool` | the pool, and the typed indices into it (`ClassPointer`, `MethodDescriptor`, `FieldDescriptor`, `DataPointer`) |
| `verification` | the verification types of JVMS 4.10.1.2, which values on the stack and in the locals have, and the expectations an instruction states for its operands |
| *(root)* | `ClassWriter`, `Writable` and `DosWriter`, which every package writes through; `ClassGraph`, `ByteClassLoader`, `Compiler` |

A body is built as a **list of instructions**, not as bytes. Nothing has an address until
`BytecodeSerializer` lays the fragment out, and that is also when `max_stack`, `max_locals`,
the stack map frames and the exception table offsets are derived. A caller never names an
offset and never describes a frame.

Everything serialisable implements `Writable`, which writes into a `ClassWriter` — a narrow
byte sink implemented once, by `DosWriter` (`u1`, `s1`, `u2`, `s2`, `int`, `float`, `long`,
`double`, `bytes`, `utf8`). Class file structures follow JVMS §4.1–4.7 one-to-one:
`ClassFile`, `FieldInfo`, `MethodInfo`, `AttributeInfo` with `CodeAttribute`,
`StackMapTableAttribute` and `BootstrapMethodsAttribute`, and `ExceptionHandler` for a row of
the `Code` attribute's exception table.

## Analysis

**`max_stack`, `max_locals` and the stack map frames are all derived; none is given.**
`Analyzer` walks the instructions from the method entry and from every handler entry,
tracking the [verification type](#instructions) of every value on the stack and in every
local slot, as the JVM's own verifier does (JVMS 4.10.1).

- **The entry** holds the arguments the descriptor declares, from slot 0 — after `this`
  for an instance method, and after an uninitialised `this` in a constructor.
- **Each instruction** applies its [effects](#instructions): what it takes is checked
  against the top of the stack, reads against the type in the slot, and what it gives and
  what it writes are recorded. The [dynamic instructions](#instructions) have a rule each —
  `aload` pushes whatever the slot holds, the `pop` forms drop whatever is on top, the
  `dup` forms copy it, `new` pushes an `Uninitialized` naming its own index, and `aaload`
  pushes whatever its array holds — `null` from the null array. `aaload` and `aastore` take
  an array of references alone; which class `aastore` stores is left to the running code, as
  the verifier leaves it, where a wrong one is an `ArrayStoreException`.
- **A constructor call** turns every copy of the object it initialises, on the stack and in
  the locals, into the class — or into this class, for the parent constructor called on
  `this`. Until then the object can be copied, kept in a local and have its constructor
  called, and nothing else takes it.
- **A handler entry** holds the exception alone on the stack, typed as the class its row
  catches — `java/lang/Throwable` for a catch-all — and the locals every instruction in its
  range starts with. A guarded constructor call contributes the locals it leaves as well,
  since it initializes every copy of its object and the JVM checks the handler against that
  state too. Rows that share a handler meet there like any other paths.
- **Where paths meet**, the stacks have to be equally deep, and each value merges: equal
  types stay, `null` and a class give the class, and two classes give what the
  `ClassHierarchy` names; anything else is refused. The locals merge the same way, except
  that a slot the paths cannot agree on becomes `top` — unusable from there on, not an
  error — and a slot only one path defined is dropped. A merge that changes what is known
  about an instruction walks on from it again, until nothing changes.

`Locals` keeps the local slots the way the verifier models them: a `long` or a `double`
takes its slot and the next, which holds `top`. Every change goes through its `write`, which
fills skipped slots with `top`, cuts a wide value written over at either half, and marks the
second half of a wide value it writes. A wide value without `top` after it is refused on
construction, so the pair cannot come apart.

From the walk:

- `max_stack` is the deepest the stack gets, in slots;
- `max_locals` is the most slots any instruction is entered with or leaves behind, which
  covers the arguments, every slot an instruction writes, and a wide value's second half;
- **a frame goes on every jump target and every handler entry** — once each, in code order,
  however many jumps reach it, and on a target that is also the next instruction. Each is a
  `full_frame` naming a `long` or a `double` once for both of its slots. Its offset delta
  from the frame before, and the offset of the `new` an uninitialised type points at, are
  filled in when the code is laid out.

The frame records in `classfile/attributes` cover every kind of JVMS §4.7.4 but
`chop_frame`; the analyzer produces `full_frame` alone.

The walk is also where a malformed body is caught: an unlinked jump, code that runs past its
last instruction or is never reached, an operand of the wrong type or missing, a local read
before it is written, paths that meet with stacks that cannot merge, an object used before
its constructor ran. Each message names the instruction and its index. Paths that cannot
meet and code that runs past its end raise an `IllegalStateException`, everything else an
`IllegalArgumentException`.

### Class hierarchy

Merging two classes and passing a value where a class is declared both need to know how
classes relate, which the analyzer cannot find out itself. It asks the `ClassHierarchy` the
class was built with:

```kotlin
interface ClassHierarchy {
    fun isAssignable(from: String, to: String): Boolean
    fun commonSuperclass(a: String, b: String): String
}

classFile(name, parent, hierarchy)
```

The default, `LenientHierarchy`, knows no classes. It accepts any class where another is
declared, leaving that check to the JVM verifier, and meets two different classes as
`java/lang/Object` — always a valid frame, but too wide for code that goes on to use the
value as something narrower.

## Constant pool

`UpdatableConstantPool` collects entries while code is generated, then freezes into a
`StaticConstantPool`. Every kind is **cached and deduplicated** — utf8, int, long, double,
class, string, name-and-type, field/method/interface refs, method handles, dynamic constants
and invokedynamic entries — so repeating a name or a descriptor costs one entry. Long and
double correctly occupy two slots.

`ClassPointer` / `DataPointer` resolve to a pool index at creation, so `clazz("A")` or
`string("boom")` registers once and the index is fixed from then on. Each also carries what
the analyzer needs to know about it: a `ClassPointer` the class name, a `DataPointer` the
type of the constant it loads.

`Pointers` hands out the `ClassPointer`s — `clazz`, `self` and `parent` — which the
instructions and helpers naming a class take. `Constants` hands out the `DataPointer`s: the
loadable constants, which `ldc` or `ldc2_w` load and a bootstrap method takes as arguments —
`int`, `float`, `long`, `double`, `string`, `methodHandle` and
[`constantDynamic`](#dynamic-constants).

`methodHandle(kind, clazz, name, descriptor)` registers a method handle — a `DataPointer`
that `ldc` loads as a `java/lang/invoke/MethodHandle`, or that a
[dynamic constant](#dynamic-constants) takes as an argument. The `ReferenceKind` picks the
ref it points at: a field ref for the four field kinds, an interface method ref for
`INVOKE_INTERFACE`, and a method ref for the rest — or an interface method ref for
`INVOKE_STATIC` and `INVOKE_SPECIAL` too, given `onInterface = true`, as a static method an
interface declares needs; no other kind can point into an interface. `NEW_INVOKE_SPECIAL`
names `<init>` and nothing else does; anything else is refused.

## Instructions

`instruction/` holds the IR. Each opcode states its encoding and what it does to the
operand stack and to the local slots:

```kotlin
data object iadd : NoArgInstruction(0x60) {
    override fun stackEffect() = takes(INTEGER, INTEGER) gives INTEGER
}

data class istore(override val index: Int) : LocalSlotInstruction(0x3b, 0x36) {
    override fun stackEffect() = takes(INTEGER)
    override fun localEffects() = listOf(Write(index, INTEGER))
}
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
  and a wide form, where `ldc2_w` has the wide one alone. `iconst`, `lconst`, `fconst`,
  `ldc` and `iinc` carry their range check in `init`; `LocalSlotInstruction` checks its slot
  as it writes, its `index` being abstract and so out of reach of the base class's `init`.
  `ldc` and `ldc2_w` also check the constant takes the slots they load: one for `ldc`, two —
  a long or a double — for `ldc2_w`.
- **`Effects.kt`** — `StackEffect` for the operand stack, `Read` and `Write` for a local
  slot. A `StackEffect` names the top of the stack an instruction expects, `before`, and
  what it leaves there, `after`, both listed bottom to top as JVMS writes
  `..., value1, value2 -> ..., result`; the analyzer checks `before` from the top down.
  `takes(...)` and `gives(...)` build one, and `takes(...) gives X` both halves;
  `StackEffect.NONE` leaves the stack as it is. The four invoke instructions share
  `Invocation`, which states a call's effect once from its argument and return types: the
  receiver and the arguments, then the result, or nothing for `void`.
- **`Instruction.kt`** — the opcodes, and the sealed interface the files above refine.
- **`Fragment.kt`** — a body as these instructions, with its jumps and handlers, and
  [`join`](#three-ways-to-define-a-method) for stitching several into one.

Effects are stated in the `verification` package, below everything else. A value has one of
the verification types of JVMS 4.10.1.2: `INTEGER`, `FLOAT`, `LONG`, `DOUBLE`, `NULL`, `TOP`,
`UNINITIALIZED_THIS` and `VOID`, `ReferenceType` for a class or an array (named as the
constant pool names it, `java/lang/String` or `[I`), and `Uninitialized` for an object `new`
made whose constructor has not run. Each knows how many slots it takes and whether it may be
handed on as a reference.

What an instruction takes is stated as an `Expected`, which is not always a single type:
`OfType` for a value of one type — for a class, also null or a class the hierarchy lets stand
for it — `AnyReference` for any object or null, and `OneOf` for an operand that may be any of
a few, as `baload` and `bastore` take a `byte[]` or a `boolean[]` alike. `takes(INTEGER)` is
short for `takes(OfType(INTEGER))`; only a list mixing the two, as `baload`'s does, spells
`OfType` out. What an instruction gives is a verification type — only values have one, so a
frame never holds an expectation.

A few instructions have no effect they could state on their own: `aload`, `astore`, `new`,
`aaload`, `aastore`, `pop`, `pop2` and the four `dup` forms move, read, store, drop or copy
whatever type the frame holds. They are marked
`DynamicInstruction`, and the analyzer has a rule for each.

`Instruction` is sealed, so its files must stay in that one package — nothing outside it
can introduce an instruction.

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
opcode: `Members` (calls, field refs and `constructDefault`), `Pointers` (refs for
classes), `Constants` (the constants `ldc` loads), and `link` / `` `catch` `` / `end` for
addressing.

`@DslMarker` (`@CodeDsl`) keeps a nested block from resolving an outer receiver.

**`+` hands back a `Label`** — the position of the instruction it added,
as an index into the instructions of the builder that handed it out. A label belongs to
that builder and nowhere else: one handed to a different builder is refused rather than
silently naming whatever sits at that index there. That single return value is the whole
addressing story, since jump targets and exception ranges are both labels.

Indices are also what makes a fragment portable. A jump or a guarded range inside one
refers to positions in its own instruction list, so splicing it somewhere else shifts every
reference by the number of instructions ahead of it and nothing else has to change —
where a byte offset would have had to be recomputed, and a jump patched.

**What is covered:**

- `int` — constants, locals, `iinc`, arithmetic, loads and stores into `int[]`, `byte[]` and
  `boolean[]`, comparisons with zero and with each other, returns;
- `float` — constants, locals, arithmetic, negation, returns;
- `long` — constants, and stores to a local;
- `double` — constants;
- references — `aconst_null`, locals, `new`, `newarray`, `anewarray`, loads and stores
  into arrays of references, `checkcast`, `instanceof`, identity and null comparisons,
  `areturn`, `athrow`;
- fields and calls — `getstatic`, `getfield`, `putfield` and the invoke family;
- the stack — `pop`, `pop2`, `dup`, `dup_x1`, `dup_x2`, `dup2`;
- the pool — `ldc` for a one slot constant, `ldc2_w` for a long or a double, either for a
  [dynamic constant](#dynamic-constants).

A float constant that has an opcode of its own (0, 1, 2) uses it; any other goes to the pool
as `ldc`. A long constant 0 or 1 is `lconst`; any other long, and any double, goes to the
pool as `ldc2_w`. `newarray` takes the `PrimitiveType` of its
element and encodes the atype code itself; `anewarray` takes a `ClassPointer` to a class, an
interface or an array, as `clazz("java/lang/String")` or `clazz("[I")`.

**Local slots widen automatically.** The loads and stores take a slot and nothing else;
which of the three encodings that slot needs is settled when the instruction is written,
as above. The ceiling is 65535, which is `max_locals`' own limit, and the
[width checks](#width-checks) reject anything beyond.

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

A call is emitted by one of four helpers, one per invoke instruction, each taking the class
that owns the method, its name and its descriptor:

```kotlin
invokestatic(self(), "f", "(I)I")
invokevirtual(clazz("java/lang/String"), "length", "()I")
invokeinterface(clazz("java/util/List"), "size", "()I")
invokespecial(parent(), "<init>", "()V")
```

Each registers the ref in the pool — an interface method ref for `invokeinterface`, a
method ref for the others — and emits the instruction with the types the descriptor
gives: the arguments, the result and, for everything but `invokestatic`, a receiver of the
owning class taken first. An `invokespecial` of `<init>` is marked as a constructor call
for the class, which is what lets the analyzer tell initialising an object apart from
calling one of its methods. `invokeinterface` writes its argument count from the same
types, a `long` or a `double` counting twice. Like `+`, each helper hands back the label of
the call, so a call can be a jump target or open a guarded range.

`field(clazz, name, descriptor)` registers a field ref and hands back a `FieldDescriptor` —
the pool index, the class that owns the field and the type it holds — which `getstatic`,
`getfield` and `putfield` take whole.

`constructDefault(clazz(...))` emits `new`, `dup` and `invokespecial` of the class's
`<init>()V`, leaving the fresh instance on the stack.

`field` only *refers* to a field, of this class or any other. A field this class owns is
declared on `ClassFileBuilder`, next to its methods, with the same `AccessFlag`s:

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

## Dynamic constants

A dynamic constant is computed by a static bootstrap method the first time it is loaded, and
every load after that gives the same value:

```kotlin
val bootstrap = "(${CBP}Ljava/lang/String;)Ljava/lang/String;"
+ldc(constantDynamic(self(), "greeting", bootstrap, string("world")))
```

`constantDynamic` takes the class that declares the bootstrap method, its name and its whole
descriptor, then the constants the method is given as its remaining arguments — any
`DataPointer`, another dynamic constant included. The descriptor starts with
`CBP`, the three parameters the JVM passes first: the lookup, the
constant's name and its type. The constant is named `_` and takes the type the method
returns, unless `constantName` and `constantType` — a field descriptor — say otherwise, which
is how a bootstrap method returning `Object`, as the JDK's `ConstantBootstraps` do, gives a
typed constant. The `DataPointer` handed back is loaded by `ldc`, or by `ldc2_w` for a long
or a double. A method whose descriptor does not start with the prefix, or that returns
`void`, is refused, and so is a constant typed `void`.

```kotlin
+ldc(constantDynamic(clazz("java/lang/invoke/ConstantBootstraps"), "getStaticFinal",
    "(${CBP})Ljava/lang/Object;", constantName = "MAX_VALUE", constantType = "I"))
```

Each constant registers a static [method handle](#constant-pool) to its bootstrap method,
a bootstrap method and the constant itself. The bootstrap methods are kept per class, shared by every code builder
it hands out and deduplicated like the pool — one handle with the same arguments is one
bootstrap method — and written as the class's `BootstrapMethods` attribute when there is at
least one.

`attribute(...)` on `ClassFileBuilder` adds any other attribute to the class; attributes are
written after the methods, in the order they were given.

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

`Fragment` is an instruction list plus its jumps, handlers and encoded length. Joining and
splicing **shift every index by the instruction count ahead of it** — not by the byte
length, which is the same number only while every instruction is one byte wide. There is
no offset arithmetic and nothing to patch afterwards: a fragment's jumps and handlers point
at positions in its own list, and moving the list moves them.

`build()` reads the instructions out into a fragment rather than freezing the builder, so it
can be called more than once and emission can carry on afterwards.

## Loading

`ClassFileBuilder.build()` hands back a `ClassDef` — the class's name and its bytes. A
program is a `ClassGraph`: the root class, which the caller instantiates, and the classes
it depends on.

```kotlin
ClassGraph(root = classFile("Main", ...).build(), deps = listOf(classFile("Helper", ...).build()))
```

`ByteClassLoader` defines classes from their bytes. `loadClass(name, bytes)` defines one
straight away; the `deps` it is constructed with are defined only when the JVM first asks
for them — to resolve a superclass, or a class a method body names — so they may be listed
in any order. Names are taken in the internal form a class file uses, `a/b/C`. Each loader
is a namespace of its own, so the same class name can be defined repeatedly — one loader per
compilation, not per JVM.

`Compiler.pipeline` runs the whole chain from source text to a live object:

```kotlin
pipeline(source, parse, generate, Program::class.java)
```

It parses the source into whatever IR `parse` returns, generates a `ClassGraph` from it with
`generate`, and gives the graph a loader of its own: the root is defined before `pipeline`
returns, the dependencies as the JVM reaches them. It then checks the root implements the
interface it was given, and instantiates it through its no-argument constructor. Anything the program needs at run time
is passed to the interface's method, not to the constructor.

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
second copy of the encoder, and the frames the analyzer derives are checked entry by entry
against the verification types they should hold. `GeneratedClassTest` loads and runs
generated classes, so the **JVM verifier** checks the pool, bytecode, frames and exception
table as well.
