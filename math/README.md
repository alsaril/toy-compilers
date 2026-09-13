# math

> **Work in progress.** The pipeline compiles and evaluates correctly, and the splitting
> and stack arithmetic below are in place, but the accessor scheme is still provisional —
> see [Known limits](#known-limits).

An arithmetic expression compiler. An expression goes in, a JVM class comes out, and the
JVM evaluates it against a map of variables — nothing is interpreted. Built on
[`codegen`](../codegen/README.md).

```bash
./gradlew :math:installDist
math/build/install/math/bin/math --expr "x*y+1" --vars "x=2;y=3" --vars "x=1;y=1"
```

## Pipeline

```
"x*y+1" ──> Parser ──> Node ──> ClassGenerator ──> class bytes ──> Program
```

- **`Parser`** — hand-written shunting yard, no parser generator.
- **`ast.kt`** — `Value`, `Var`, `Neg` and `Op(kind, left, right)`, a tree.
- **`ClassGenerator`** — the class shell, the variable accessor, and the expression body
  including all method splitting.

## Parser

Two stacks, operands and operators, reduced when an operator of at least equal priority
arrives — so `1+2*3` groups as `1+(2*3)` and `1-2-3` as `(1-2)-3` rather than the reverse.
Iterative, so nesting is bounded by the heap rather than the call stack.

**Operands and operators have to alternate**, and tracking which of the two is due is what
lets every malformed expression be reported at the character that broke it:

```
1+      operand expected at 2
1 2     operator expected at 2
(1+2    ')' expected at 4
1^2     unexpected '^' at 1
```

That is one exception type with a position in every message, which is what the CLI turns
into `could not compile expression: operand expected at 2`. A `reports every malformed
expression the same way` test holds the whole vocabulary to it.

**Unary minus** is the one operator with two readings. `-` is unary exactly when an operand
is due, so `-1`, `2*-3` and `--5` all parse, while `1--2` is a subtraction of a negation.
It binds tighter than any binary operator, and a bracket reduces a pending negation before
matching — otherwise `(-3)` would pop the negation in place of the bracket.

## Code generation

Non-recursive throughout. Collecting variables is a pre-order walk over an explicit stack;
emitting code is a stack of **work items** — `Visit(node)` for a subtree and `Apply(kind)`
for an operator waiting on the operands below it:

```kotlin
is Op -> {
    work.addLast(Apply(node.kind))   // pushed first, so popped last
    work.addLast(Visit(node.right))
    work.addLast(Visit(node.left))   // pushed last, so popped first
}
```

Post-order falls out of the push order, so nothing has to remember how many children it has
already handled. Generation is bounded by the heap: a 100 000 term expression compiles.

### The `getFloat` indirection

Reading a variable is four instructions — `Map.get`, a type test, a cast and `floatValue` —
plus a branch and a throw for the absent case. Emitting that at every occurrence would cost
more than the arithmetic around it, so it is emitted **once**, as a private static helper:

```
getFloat(Map, String) -> float     // get, instanceof, checkcast, floatValue, or throw
```

`instanceof` rather than a bare cast is what makes the failure reportable: a missing key
gives `null`, which passes a `checkcast` and only dies later at `floatValue` with a message
naming neither the variable nor the cause. Testing first lets the throw carry the name, so
`x+1` evaluated against `y=1` reports `no variable with name x is found`.

### The accessor preamble

Each variable is read into a local slot **once per call**, before any arithmetic, and the
body then uses `fload`. So `x*x+x` costs one map lookup, not three, and the expression
proper is pure stack arithmetic.

Slots are assigned in the order names first appear, with slot 0 holding the map:

| slot | holds |
|---|---|
| 0 | the `Map` argument |
| 1…n | one float per distinct variable |

### Outlining

A JVM method is capped at 65535 bytes, and HotSpot will not JIT-compile anything over
**8000** — so that is the budget. When a subtree's bytecode outgrows it, the subtree becomes
its own `static` method `fN(Map) -> float` and the parent emits `aload_0; invokestatic` in
its place. Two cases trigger it: one operand too big on its own, and two operands that only
overrun together.

A generated class is therefore:

```
<init>   getFloat   f0 … fN   eval
```

where `eval(Map)` is a five-byte trampoline into the last `fN`. Outlined methods call each
other linearly rather than nesting, so the call chain stays shallow: 20 000 terms compile
to 9 methods, 100 000 to 31.

### `Context(maxStack, delta)`

`max_stack` cannot be read off a fragment, because a fragment's peak is relative to
wherever it lands. Each subtree therefore carries two numbers alongside its builder:

| | |
|---|---|
| `maxStack` | the deepest the stack gets while this subtree runs |
| `delta` | how much it leaves behind — 1 for an expression |

which compose at a binary operator:

```kotlin
maxStack = max(left.maxStack, left.delta + right.maxStack)
delta    = left.delta + right.delta - 1
```

The right operand runs *on top of* whatever the left one left, hence the offset; the
operator pops two and pushes one, hence the `- 1`. A leaf is `(1, 1)`, negation leaves both
untouched — `fneg` replaces its operand — and an outlined subtree collapses to `(1, 1)`,
since a call is one instruction leaving one value.

The result is exact rather than conservative: a right-leaning tree of *n* terms declares
exactly *n*, and a left-leaning one declares 2 however long it runs, because each operator
consumes its left operand before the next arrives.

**One door.** `CodeBuilder` keeps its own `max_stack` accumulator, and it cannot be the same
number — the builder measures from its own start and knows nothing of `delta`. The two are
reconciled in exactly one place:

```kotlin
fun build(): Fragment = codeBuilder.apply { maxStack(maxStack) }.build()
```

so a fragment can never leave `materialize` without its depth applied. Every path that
turned a builder into a fragment used to do this by hand, and two of the three forgot —
each one a class the verifier rejected with `Operand stack overflow`.

Splitting bounds the depth as a side effect: a body capped at ~7200 bytes at ~2 bytes per
term cannot need more than ~3600 slots, so the `u2` ceiling on `max_stack` is unreachable
by construction.

## Runtime model

The generated class implements `Program`, and that is its whole surface:

```kotlin
interface Program {
    fun eval(variables: Map<String, Float>): Float
}
```

Compilation happens once and evaluation many times, which is the point of compiling at all.
Arithmetic is `float` throughout — `fadd`, `fsub`, `fmul`, `fdiv`, `fneg` — so division by
zero gives `Infinity` and `0/0` gives `NaN` rather than raising, and `2^24 + 1` stays
`2^24`.

The CLI is two commands behind one name:

```
Usage: math [<options>]

Options:
  --interactive  prompt for the expression and for each set of variables,
                 keeping going after anything that fails
  --expr=<text>  the expression to evaluate, required unless --interactive is given
  --vars=<text>  assignments to evaluate the expression against, separated by ';'.
                 Repeat for a line of output each
```

Given `--expr` and `--vars` the whole job is known up front, so it answers once per set, in
order, and a set that cannot be evaluated writes `failed` in its place with the reason on
standard error — the results stay lined up with the sets they came from. Only an expression
that will not compile stops the run. Given `--interactive` there is always a next line, so
everything short of running out of input is reported and asked again, including an
expression that will not compile.

Argument parsing is [Clikt](https://github.com/ajalt/clikt), as in
[`bf`](../bf/README.md); the compiler behind it pulls in nothing.

## Known limits

Measured, not estimated:

- **The accessor is emitted into every method.** Lookups therefore scale with method count
  rather than variable count: four variables across twelve methods cost 36 map lookups
  where four would do. Unpacking only the variables a method actually uses is the obvious
  fix and is not done yet.
- **The split budget ignores the accessor prefix.** The check measures the body alone, and
  `defineMethod` then prepends the accessor, so a method overruns 8000 at around 90
  variables — 128 variables produce an 8238 byte method. Not a correctness failure, since
  8000 is HotSpot's threshold rather than the format's, but those methods silently stay
  interpreted, which is the one thing the budget exists to prevent.
- **Evaluation runs out of stack around 150 000 terms.** Generation is fine; it is the call
  chain at runtime, each frame carrying up to ~3600 operand slots. Raising the method
  length budget makes this worse, not better, by trading more frames for deeper ones.
- **The split criterion is length, not depth.** `loc()` says nothing about stack usage, so a
  deep expression is divided where it reaches 7200 bytes rather than where the stack gets
  deep.
- **Identifiers are letters only**, and there is no unary plus, no exponentiation and no
  functions.

## Tests

`ParserTest` covers precedence, associativity, brackets, unary minus, leading-point numbers
and every way an expression can be malformed, including a property test that holds every
failure to one type and a position. `ClassGeneratorTest` drives `generate` directly and
runs the result: arithmetic and float semantics, the variable accessor (including that a
repeated variable is looked up once, in AST order, and again on the next call), stack depth
against the class file's declared `max_stack`, and splitting — that an outlined body still
resolves its variables, still names a missing one, and declares the depth it needs.
`MainTest` covers both CLI modes and every way a command line or an input line can be wrong.
