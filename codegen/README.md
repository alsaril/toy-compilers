# codegen

Writes JVM class files by hand — constant pool, fields, methods, bytecode, stack map frames,
exception handlers and bootstrap methods — with no bytecode library underneath. It knows the
class file format and nothing about any source language. Classes are written as version 65.

A method body is given as instructions alone. `max_stack`, `max_locals` and the stack map
frames are derived by an [analysis](#frame-analysis) that tracks the type of every value and
refuses a body the verifier would not load.

## Packages

Dependencies run one way, top to bottom:

| package | holds |
|---|---|
| `code` | `ClassFileBuilder`, `CodeBuilder` and the helpers around them — everything that builds |
| `assembly` | `Analyzer`, `Locals`, `ClassHierarchy` and `BytecodeSerializer` — what turns a built body into a `Code` attribute |
| `instruction` | the opcodes, their encodings and effects, and `Fragment` |
| `classfile` | the class file records and their attributes, and descriptor parsing |
| `constantpool` | the pool and the typed indices into it |
| `verification` | the types the analysis tracks, and what an instruction expects of its operands |
| *(root)* | `ClassWriter` and `DosWriter`, which every record writes through; `ClassGraph`, `ByteClassLoader`, `Compiler` |

## Building a class

```kotlin
val (name, bytes) = classFile("Counter", parent = "java/lang/Object")
    .iface("java/util/function/IntSupplier")
    .field("count", "I", PRIVATE)
    .method("<init>", "()V", PUBLIC) {
        +aload(0)
        invokespecial(parent(), "<init>", "()V")
        +`return`
    }
    .method("getAsInt", "()I", PUBLIC) {
        +aload(0)
        +getfield(field(self(), "count", "I"))
        +ireturn
    }
    .build()
```

`classFile(name, parent)` starts a class; `iface`, `field` and `method` add to it in the
order they are called, and `attribute` adds any other class attribute. `build()` hands back
a `ClassDef` — the name and the bytes — and can be called again. A method named `<init>` is
analysed as a constructor and one flagged `STATIC` as having no `this`. Fields are written
without attributes, so a field starts at its default value and code sets it.

## Emitting bytecode

Inside a method block the receiver is a `CodeBuilder`, and an instruction is emitted by
naming it after `+`:

```kotlin
method("max", "(II)I", PUBLIC, STATIC) {
    +iload(0)
    +iload(1)
    val jump = +if_icmplt
    +iload(0)
    +ireturn
    link(jump, +iload(1))
    +ireturn
}
```

`+` is the only way an opcode gets in, and it hands back a `Label` — the instruction's
position, owned by the builder that handed it out. Everything that addresses code takes a
label:

- **jumps** — `link(jump, target)`, before or after the target exists;
- **handlers** — `` `catch`(from, to, handler, type) `` guards `[from, to)`, with `end()` as
  the label past the last instruction and a `null` type catching anything. A range covering
  no instruction is refused.

The instruction classes are named after their mnemonics in lower case, `` `return` `` being
the one escaped. An instruction picks its own encoding from its operand: a local slot, an
`iconst` value or a pool index gets the shortest form that holds it, so changing the operand
re-encodes it. `iconst` reaches a `short`, `fconst` 0 to 2 and `lconst` 0 and 1; past that a
constant comes from the pool, as `ldc(int(…))`, `ldc(float(…))` or `ldc2_w(long(…))`.

Around `+` sit the helpers that register what an instruction refers to and hand back a
typed pointer to it:

- **classes** — `clazz(name)`, `self()`, `parent()`, giving a `ClassPointer`;
- **constants** — `int`, `float`, `long`, `double`, `string`, `methodType`,
  `methodHandle(kind, clazz, name, descriptor)` and [`constantDynamic`](#dynamic-call-sites-and-constants),
  giving a `DataPointer` that `ldc` loads — `ldc2_w` for a long or a double;
- **fields** — `field(clazz, name, descriptor)`, giving the `FieldDescriptor` that
  `getfield`, `putfield` and `getstatic` take;
- **calls** — `invokevirtual`, `invokespecial`, `invokestatic` and `invokeinterface`, each
  taking the owner, the name and the descriptor and emitting the call, and
  [`invokedynamic`](#dynamic-call-sites-and-constants);
- `constructDefault(clazz)` — `new`, `dup` and the no-argument constructor.

Covered so far: `int` arithmetic, `iinc`, comparisons and `int[]`, `byte[]` and `boolean[]`
access; `float` arithmetic; `long` and `double` constants and `long` stores; objects, arrays
of references, casts, type tests and reference comparisons; fields; every invoke
instruction; `pop` and the `dup` forms; returns and `athrow`.

## Fragments

A body does not have to be emitted in one go. `emitFragment { … }` builds a `Fragment` — the
instructions, their jumps and handlers, and their encoded length — without adding a method,
and there are two ways to put fragments together:

```kotlin
val prefix = emitFragment { /* … */ }
val body = emitFragment { /* … */ }

method("f", "()V", listOf(prefix, body).join(), PUBLIC)  // joined up front

method("g", "()V", PUBLIC) {                             // spliced while emitting
    fragment(prefix)
    +nop
    fragment(body)
}
```

Jumps and handlers name instructions by index within their own fragment, so joining or
splicing shifts each by the number of instructions ahead of it and nothing else changes —
no offsets are patched, since none exist until the method is laid out. `fragment(...)`
hands back the label of the first instruction it spliced in, or `null` for an empty one.

`newCodeBuilder()` hands out more builders on the same class, sharing its constant pool and
bootstrap methods, so pieces can be built apart. `build()` on a builder reads its fragment
out and leaves it open for more, and `transform { index, instruction -> … }` replaces
instructions in place, re-measuring the length.

## Dynamic call sites and constants

A class keeps one table of bootstrap methods, shared by every builder it hands out.
`bootstrap(handle, args...)` adds a method handle and its static arguments — any
`DataPointer`s — to it and hands back a `BootstrapPointer`; an equal handle with equal
arguments gets the same pointer. The table is written as the class's `BootstrapMethods`
attribute, and only when it holds something.

Both dynamic forms take that pointer, with a name and a type:

```kotlin
val bind = methodHandle(INVOKE_STATIC, self(), "bind", "(${IDP}Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/CallSite;")
invokedynamic("_", "(I)I", bootstrap(bind, methodHandle(INVOKE_STATIC, self(), "impl", "(I)I")))

val read = methodHandle(INVOKE_STATIC, clazz("java/lang/invoke/ConstantBootstraps"), "getStaticFinal", "(${CBP})Ljava/lang/Object;")
+ldc(constantDynamic("MAX_VALUE", "I", bootstrap(read)))
```

- `invokedynamic(name, descriptor, bootstrap)` emits a call site of that method descriptor,
  taking its arguments with no receiver. Equal call sites share a pool entry.
- `constantDynamic(name, type, bootstrap)` registers a constant of that field type and hands
  back its `DataPointer`, which `ldc` loads and another bootstrap method can take as an
  argument. A `void` type is refused.

`IDP` and `CBP` are the parameter lists a bootstrap method of each starts with, for writing
its descriptor. `lambdaBootstrap(interfaceMethodType, implementation, dynamicMethodType)` registers
`LambdaMetafactory.metafactory` with those three as its arguments.

`methodHandle` picks the ref it points at from its `ReferenceKind`: a field ref for the field
kinds, an interface method ref for `INVOKE_INTERFACE`, a method ref otherwise — or an
interface method ref for `INVOKE_STATIC` and `INVOKE_SPECIAL` given `onInterface = true`.
`onInterface` with any other kind is refused, and so is `<init>` with any kind but
`NEW_INVOKE_SPECIAL` or anything else with it.

## Frame analysis

When a method is added, `Analyzer` walks its body from the entry and from every handler,
tracking the type of every value on the stack and in every local slot, and derives:

- `max_stack` — the deepest the stack gets, in slots;
- `max_locals` — the most slots any instruction is entered with or leaves behind;
- **a frame on every jump target and every handler entry**, once each, as a `full_frame`.
  Its offset, and the position of the `new` an uninitialised object came from, are filled
  in when the code is laid out.

Each instruction states what it does to the frame, and the walk checks it and applies it:

```kotlin
data object iadd : NoArgInstruction(0x60) {
    override fun stackEffect() = takes(INTEGER, INTEGER) gives INTEGER
}
```

`takes(...)` lists what it expects on top of the stack, bottom to top, each as an `Expected` —
one type, any reference, or one of a few — and `gives(...)` what it leaves there; `Read` and
`Write` name the local slots it uses. The few whose effect depends on what is there —
`aload`, `astore`, `new`, `aaload`, `aastore`, `pop`, `pop2` and the `dup` forms — are marked
`DynamicInstruction`, and the analyzer has a rule for each.

The rest of the walk:

- **The entry** holds the arguments the descriptor names — after `this` for an instance
  method, and after an uninitialised `this` in a constructor.
- **A constructor call** turns every copy of the object it initialises, on the stack and in
  the locals, into its class. Until then the object can only be copied, stored and
  initialised.
- **A handler** starts with the caught exception alone on the stack, typed as the class it
  catches, and the locals every instruction in its range starts with — and, after a guarded
  constructor call, the locals that call leaves.
- **Where paths meet**, the stacks must be equally deep. Equal types stay, `null` and a class
  give the class, two classes give their common superclass; anything else is refused. A
  local the paths disagree on becomes unusable, and one only some paths set is dropped. A
  meeting that changes what is known walks on from there again until nothing changes.

`Locals` keeps a `long` or a `double` as its slot and the next one, and every write keeps the
pair together — overwriting either half drops the value.

How classes relate comes from the `ClassHierarchy` a class is built with,
`classFile(name, parent, hierarchy)`, which answers `isAssignable(from, to)` and
`commonSuperclass(a, b)`. The default, `LenientHierarchy`, knows no classes: it lets any
class stand for another and meets two of them at `java/lang/Object` — a frame that always
loads, but too wide for code that goes on to use the value as one of the two.

A malformed body is refused with a message naming the instruction and its index: an
unlinked jump, a missing or mistyped operand, a local read before it is set, paths that
cannot meet, an object used before its constructor ran, code that runs off its end or is
never reached. Code that runs off its end and paths that cannot meet raise
`IllegalStateException`, the rest `IllegalArgumentException`.

## Other details

- **Constant pool** — every kind of entry is deduplicated, so a repeated name, descriptor or
  constant costs one entry. A pointer resolves to its index when it is created.
- **Width checks** — a value that does not fit its field is refused where it is written,
  naming the value and the width: by `DosWriter`, which every byte passes through, and by
  each instruction as it is constructed, so a bad operand fails at the `+`. A branch offset
  is checked once layout knows it, and a method's code must be 1 to 65535 bytes.
- **Loading** — a program is a `ClassGraph`, a root class and the classes it depends on.
  `ByteClassLoader` defines the root straight away and each dependency when it is first
  needed, so they may be listed in any order. `Compiler.pipeline(source, parse, generate,
  iface)` parses, generates the graph, loads it with a loader of its own, checks the root
  implements `iface` and instantiates it through its no-argument constructor.
