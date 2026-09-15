# math

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
- **`generator/ClassGenerator`** — the class shell: the constructor and the `getFloat` helper.
- **`generator/EvalGenerator`** — the accessor, the expression body, and all method splitting.
- **`generator/VirtualInstructions`** — the half-encoded body and the usage counts that
  decide its slot numbering.
- **`generator/utils.kt`** — collecting the variables of a tree, and a counter.

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
emitting code is a stack of **`(node, results)`** pairs, where `results` holds what the
node's children have come back with so far. A node is peeked rather than popped, and how
many results it already has says what to do next:

```kotlin
is Op -> {
    when (results.size) {
        0 -> stack.add(node.left to mutableListOf())   // left not started
        1 -> stack.add(node.right to mutableListOf())  // left done, right next
        else -> null                                   // both in, emit the operator
    }?.let { continue }

    val (leftResult, rightResult) = results
    …
}
```

`results` is the call stack's frame made explicit — the one thing a recursive walk gets for
free is knowing where it was, and this is the price of not recursing. A leaf never pushes
anything and returns immediately; `ret` pops the finished node and appends its `Context` to
the parent's `results`. Generation is then bounded by the heap: a 100 000 term expression
compiles, and so does a million term one.

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

| slot | holds |
|---|---|
| 0 | the `Map` argument |
| 1…k | one float per variable **this method** reads |

**Each method carries only what it uses.** A subtree records the variables it touched, and
a method reads exactly those — so splitting an expression in half does not make both halves
pay for every name in it.

**Slots are numbered by usage, not by appearance.** The variables a method reads most often
get the lowest slots, which are also the ones with a one-byte `fload` of their own. Slots
0–3 encode in one byte, up to 255 in two, and beyond that the wide form takes four, so
putting the hottest variables first shortens the body as well as tidying the frame.

That is only possible because a body is emitted **twice over**. The first pass keeps
everything as bytes except the variable accesses, which stay symbolic:

```kotlin
context.exact { fadd() }   // ExactInstruction: a Splice, the bytes taken from the builder
context.fload(index)       // LoadInstruction: still a name's index, not a slot
```

A `Splice` is [`codegen`](../codegen/README.md#splices)'s handle on a run of bytes already
emitted: opaque, so the only thing to be done with one is hand it back to a builder. Each
`exact` call produces one, so the half-encoded body is an alternation of byte runs and
loads — roughly one run per tree node, since every operator ends one.

A body is built before it is known which method it will land in, and a method numbers its
slots from the variables it turned out to use — so the loads cannot be encoded until that
is settled. `VirtualInstructionsBuilder` holds the half-encoded form and the usage counts;
`defineMethod` decides the numbering and emits the real instructions. Alongside it a plain
`CodeBuilder` is kept purely as a **size estimate**, since the split decision has to be made
before any of this is known.

### Outlining

A JVM method is capped at 65535 bytes, and HotSpot will not JIT-compile anything over
**8000** — so that is the budget. When a subtree's bytecode outgrows it, the subtree becomes
its own `static` method `fN(Map) -> float` and the parent emits `aload_0; invokestatic` in
its place. Two cases trigger it: one operand too big on its own, and two operands that only
overrun together.

**The preamble is part of the method, so it is part of the estimate.** Its length depends
only on how many variables a method reads, since compaction numbers their slots `0…k-1`
whichever ones they are — so three measured line costs, one per store width, give every
answer:

```kotlin
lines[0] * min(k, 3)  +  lines[1] * clamp(k - 3, 0, 252)  +  lines[2] * max(k - 255, 0)
```

The three are measured by emitting a line rather than predicting its length, so they follow
any change to the emitter — the same reason [`bf`](../bf/README.md) measures its own
budgets. Only the `ldc` is predicted, and only by a byte: it widens once a name's constant
pool index passes 255, so the line is measured against the first name — whose index is the
lowest — and one byte is added for the wide form the rest may need.

**The budget is under 8000, and the difference is an allowance rather than slack.** A merge
checks `length(left) + length(right)`, which is not the merged length. Two halves may share
variables, in which case the sum over-counts and something is split that needn't have been.
Or they may share none — and then the merge pushes variables into a wider store band that
neither half was in. Two halves of 255 variables each sit wholly in the two-byte band; their
union of 510 puts 255 of them in the four-byte one:

```
prelude(255) = 2292        2 x 2292 = 4584
prelude(510) = 5097        excess   =  513
```

So a merged method can reach `budget + 513`, and the excess saturates there — it cannot grow
with the expression, and it does not compound up the tree, because after a merge the context
knows its own union and every later check is exact. Holding the budget 513 below 8000 covers
it by construction, which is where the load factor comes from. Measuring the union instead
of summing two preludes would remove the excess exactly, at the cost of a set operation at
every operator — 500 bytes of a 65535 byte format limit, so the approximation stays.

A generated class is therefore:

```
<init>   getFloat   f0 … fN   eval
```

where `eval(Map)` is a five-byte trampoline into the last `fN`. Outlined methods call each
other linearly rather than nesting, so the call chain stays shallow: 20 000 terms compile
to 11 methods, 100 000 to 53, a million to 535.

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

Splitting bounds the depth as a side effect: a body capped at ~7500 bytes at ~2 bytes per
term cannot need more than ~3700 slots, so the `u2` ceiling on `max_stack` is unreachable
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

- **`ldc` is over-counted by at most 127 bytes per method.** The estimate assumes the wide
  form for every name; names whose pool index is still under 255 do not need it. Each name
  costs two pool entries, a `Utf8` and a `String`, so the index crosses 255 at about the
  127th name and every name after that genuinely needs the wide form. The over-count
  therefore saturates rather than growing with the expression.

- **Evaluation runs out of stack around 250 000 right-leaning terms.** Generation is fine;
  it is the call chain at runtime, and the ceiling is a matter of shape rather than size,
  because a frame is sized by the depth its method reaches. A right-leaning body declares
  3744 operand slots, and ~130 such frames nested exhaust a default stack: 240 000 terms
  evaluate, 250 000 overflow. A left-leaning body declares 4, so the same chain costs
  nothing and a million terms evaluate through 535 methods. Raising the method length budget
  makes the right-leaning case worse, not better, by trading more frames for deeper ones.

- **Generation is linear only because splitting makes it so.** Merging appends the right
  operand to the left, so a right-leaning tree copies the accumulated side at every step.
  Below the split threshold that is quadratic — time per doubling measured at 2.5x, 3.0x,
  3.5x — and above it outlining caps each copy at the budget and it settles to 2.0x. The
  constant differs by shape: at 80 000 terms, right-leaning takes ~1.9 s against
  left-leaning's ~40 ms.

- **The split criterion is length, not depth.** `loc()` says nothing about stack usage, so a
  deep expression is divided where it reaches the byte budget rather than where the stack
  gets deep.

- **Identifiers are letters only**, and there is no unary plus, no exponentiation and no
  functions.

## Tests

`ParserTest` covers precedence, associativity, brackets, unary minus, leading-point numbers
and every way an expression can be malformed, including a property test that holds every
failure to one type and a position.

`ClassGeneratorTest` drives `generate` directly and runs the result: arithmetic and float
semantics, and the variable accessor — that a repeated variable is looked up once, in the
order its slot was numbered, and again on the next call. Since ties in the usage count keep
the order the names first appeared, an expression using each name equally is looked up left
to right, which is what most of those tests read as.

Three things there are invisible from running a class, so they are read back out of the
bytes by `GeneratedMethods`:

| | |
|---|---|
| `maxStacks` | that the declared depth is exactly what the tree reaches, not merely enough |
| `maxLocals` | that a method's slots are numbered from the variables it uses, with no gaps |
| `codeLengths` | that no method passes 8000 once the preamble is counted |

The last is sized deliberately: at 300 variables a wrong store width still fits inside the
load factor's allowance, so the test uses 600, where it cannot. A tolerance-based assertion
has to be sized against the tolerance or it quietly tests nothing.

`MainTest` covers both CLI modes and every way a command line or an input line can be wrong.
