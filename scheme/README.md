# scheme

A compiler for a small subset of Scheme, behind a REPL. Every line typed goes in, a JVM
class comes out — the expression and every lambda in it — and the JVM runs it; nothing is
interpreted. Built on [`codegen`](../codegen/README.md).

```bash
./gradlew :scheme:installDist
scheme/build/install/scheme/bin/scheme
```

```
scheme> (define (fact n) (if (= n 0) 1 (* n (fact (- n 1)))))
scheme> (fact 10)
3628800
scheme> (define (make-counter) (define n 0) (lambda () (set! n (+ n 1)) n))
scheme> (define c (make-counter))
scheme> (list (c) (c) 'done)
(1 2 done)
scheme> (car '())
runtime error: car: expected a pair, got ()
scheme> (if)
syntax error: if: expected 2 or 3 operands, got 0 in (if)
```

## Language

| | |
|---|---|
| values | 32-bit integers, `#t` and `#f`, symbols, pairs and the empty list, procedures |
| special forms | `quote` and `'`, `define` (of a value, or `(define (f args…) body…)`), `set!`, `if`, `lambda`, `and`, `or` |
| builtins | `+ - * / max min abs`, `= < > <= >=`, `cons car cdr list list-ref list-tail`, `not`, `boolean? number? symbol? pair? null? list?` |

Only `#f` is false; `0` and `'()` are true. Arithmetic wraps around like Java's `int`, and `/`
truncates; given one argument, `-` negates it and `/` divides 1 by it. A lambda takes its parameters as a proper list, a single symbol for all of them,
or a dotted list for a rest parameter — `(lambda (a . rest) …)`. Its body is any number of
expressions, of which the last is the value; a `define` among them is local to the call.

## Pipeline

```
"(+ 1 2)" ──> Tokenizer ──> Parser ──> Node ──> ClassGenerator ──> class bytes ──> Program
```

- **`tokenizer/`** — brackets, `.` and `'`, and between them runs of any other characters:
  a run that is a sign and digits is a number, anything else a symbol — `-5`, but `1+`,
  `add+one` and `1abc`.
- **`parser/`** — `Number`, `Symbol`, `Special`, `Cell(first, second)` and `Null`: source as
  data, the way Scheme reads it. `#t`, `#f` and the names of the special forms are read as
  `Special`, every other name as `Symbol`. `'x` is read as `(quote x)`.
- **`compiler/ClassGenerator`** — the class a line compiles to: the program, and its lambdas
  as methods of it.
- **`compiler/SchemeCompiler`** — `compile(source)`, handing the parts to `codegen`'s pipeline.
- **`runtime/`** — what the generated code calls: values, environments, builtins, `Binder`,
  `Dispatch`.

## Code generation

A line compiles to one class, `Impl`, which implements `Program`:

```kotlin
interface Program {
    fun run(environment: Environment): Any
}
```

and every `lambda` in the line, nested ones included, becomes a private static method
`lambdaN` of it. Each line's class is defined as a hidden class, in the compiler's package,
so every line's class can be `Impl`, and with class data holding the line's quoted data. Classes from earlier lines stay alive as long as
something in the environment still refers to one of their procedures.

### Expressions

Every expression leaves exactly one `Object` on the stack, and the current environment is
always in local slot 1 — `run` receives it there, and a lambda's method puts it there before
its body starts. So a single emitter serves the top level and every lambda body alike.

| expression | code |
|---|---|
| `42` | `ldc 42; Integer.valueOf` |
| `#t`, `#f` | `getstatic Boolean.TRUE` / `Boolean.FALSE` |
| `x` | `aload_1; ldc "x"; Environment.resolve` |
| `'x`, `'(1 x)` | `ldc` of a dynamic constant, the datum's entry in the class data |
| `(f a b)` | `f`, `Procedures.procedure`, then the arguments, `Nil.INSTANCE` and `Cons.of` once per argument, then `Function.call; Dispatch.dispatchFully` — or, in tail position, `Dispatch.of` |

**Variables are looked up by name when they run.** `resolve` walks from the innermost
environment out to the global one, so a procedure can call another that is defined after
it, and a redefinition is seen by everything already compiled.

**A quoted datum is built once, while compiling.** The generator turns it into runtime
values — `Cons`, `Nil`, `Integer`, `Boolean` and `Symbol` — and adds it to the class data, a
list with one entry per quote on the line. Its `ldc` loads that entry through
`MethodHandles.classDataAt`, so a quote evaluates to the same object every time it runs.
Symbols come from `Symbol.of`, which keeps one `Symbol` per name for the whole process, so
`'x` is the same object on every line.

**A call is checked before it is made.** The operator goes through `Procedures.procedure`,
which casts it to `Function` or throws `5 is not a procedure`; the arguments are then
evaluated left to right and consed into one list, which `call` receives as its only
argument. Builtins are plain Kotlin objects implementing the same `Function`, so they are
values like any lambda: `(list car)` is a list of one procedure.

**`if`, `and` and `or` compare references.** A value is false exactly when it is
`Boolean.FALSE`, which is one `if_acmpeq` against `getstatic Boolean.FALSE`. That holds
because every boolean in the runtime is one of the two canonical instances — the generated
code loads them by `getstatic`, and the Kotlin builtins box through `Boolean.valueOf`. `and`
and `or` keep the value on the stack and jump out as soon as one decides the result, so
`(or 2 3)` is `2` and the rest are never evaluated. An `if` without an alternative, a
`define` and a `set!` evaluate to `Unspecified.INSTANCE`.

**A call in tail position is returned, not made.** The last expression of a lambda body or
of the line is in tail position, and so are the branches of an `if` and the last operand
of an `and` or `or` in tail position. A call there evaluates its operator and arguments,
then returns `Dispatch.of(function, args)` in place of calling. Every other call is
followed by `Dispatch.dispatchFully`, which calls a returned `Dispatch`'s function with its
arguments until a value comes back, and `run` does the same with the line's value. So a
loop written as a tail call, mutual recursion included, runs in constant stack, and a
`Dispatch` never reaches a variable, a list or a condition.

### Lambdas

A lambda is a private static method of the line's class, taking the environment it was
created in and its arguments:

```
Impl implements Program
  run(Environment) -> Object
  private static lambdaN(Environment scope, Object args) -> Object  // binds args, then runs the body
```

Evaluating `(lambda …)` is one `invokedynamic`, which captures the current environment and
hands back a `Function` whose `call` runs `lambdaN` with it — the call site is bootstrapped
by `LambdaMetafactory`, through codegen's
[`lambdaBootstrap`](../codegen/README.md#dynamic-call-sites-and-constants):

```
aload_1                                       // the current environment
invokedynamic call:(Environment)Function      // a Function running lambdaN
```

The whole current environment is captured, not a selection of free variables, which is what
lets a closure both read and `set!` the variables around it. `(define (f …) …)` is the same
thing bound to `f`.

`lambdaN` starts by binding. `Binder.bind(args, names, rest, scope)` makes a
`LocalEnvironment` whose parent is `scope`, defines each parameter from the front of the
argument list, gives the rest parameter whatever is left, and throws on a count that does
not fit. The result is stored over `args` in slot 1, and the body runs with it. For
`(define (add a . rest) (+ a (car rest)))`:

```
 0: aload_1                     // args
 1: ldc       Dynamic _:List    // the parameter names, ["a"]
 3: ldc       "rest"            // the rest parameter, or aconst_null
 5: aload_0                     // scope
 6: invokestatic Binder.bind
 9: astore_1                    // slot 1 is now the call's environment
    …                           // (+ a (car rest))
75: areturn
```

A body of several expressions pops every value but the last.

### Parameter names as a dynamic constant

`Binder` needs the parameter names as a `List<String>` on every call. The list is a
[dynamic constant](../codegen/README.md#dynamic-call-sites-and-constants): computed the first time the `ldc`
runs and the same object on every run after, so a call allocates nothing for it and the
class needs no static initializer:

```kotlin
val invoke = methodHandle(INVOKE_STATIC, clazz("java/lang/invoke/ConstantBootstraps"), "invoke",
    "(${CBP}Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/Object;")
val listOf = methodHandle(INVOKE_STATIC, clazz("java/util/List"), "of", "([Ljava/lang/Object;)Ljava/util/List;", onInterface = true)
+ldc(constantDynamic("_", "Ljava/util/List;", bootstrap(invoke, listOf, *names.map(::string).toTypedArray())))
```

`ConstantBootstraps.invoke` is the JDK's general-purpose bootstrap: it calls the method
handle it is given with the remaining static arguments, here `List.of("a", …)`. No bootstrap
code is generated, and the list is immutable, so sharing one across calls is safe. The
names are ordinary `String` constants in the pool, the same entries the body's `resolve`
calls load.

## Runtime model

`GlobalEnvironment` holds the builtins and every top-level `define`, and lives as long as
the REPL; `LocalEnvironment` is one call's parameters and local `define`s, chained to the
environment its lambda was created in. `define` always writes to the innermost environment,
`set!` to the nearest one that already has the name.

Errors are three exceptions, each with a message saying what was wrong and where:

| exception | thrown | example |
|---|---|---|
| `SchemeSyntaxException` | while tokenizing, parsing or compiling — nothing has run | `lambda: parameter x is declared more than once in (lambda (x x) x)` |
| `SchemeNameException` | at run time, for a symbol no environment defines | `set!: x is not defined` |
| `SchemeRuntimeException` | at run time, for anything else | `procedure (a b): expected 2 arguments, got 1` |

The REPL creates one `GlobalEnvironment`, then reads a line at a time until the input runs
out, compiling and running each. It prints the value, or nothing for an unspecified one, so
a `define` is silent. An error is printed with its kind — `syntax error:`, `name error:`,
`runtime error:` — and the next line is read with the environment as the failed one left
it. A recursion that runs out of stack is reported as `runtime error: stack overflow`, and
an expression the compiler runs out of stack on as `compile error: stack overflow`.

## Known limits

Measured, not estimated:

- **8 189 operands per call.** A call's arguments all stay on the operand stack until the
  list is consed, and every expression is one method, so `(list 1 1 …)` with one number
  more reaches the 64 KB method limit and is refused by `codegen` with an
  `IllegalArgumentException`.
- **1 985 levels of nested calls.** `(+ 1 (+ 1 … 0))` one level deeper reaches the same
  method limit.
- **About 9 400 elements per quoted list, about 5 100 levels of nested quoted lists.** The
  generator recurses down a quoted datum to build it, so a longer or deeper one overflows
  the stack while compiling.
- **The REPL reports a stack overflow while compiling** as `compile error: stack overflow`
  and keeps going. The method limit is not caught, and ends the REPL.
- **About 4 000 levels of recursion outside tail position.** Every such call is a JVM call,
  so `(+ 1 (f (- n 1)))` runs out of the REPL's stack at around that depth. Calls in tail
  position take no stack.
- **One expression per line.** The REPL compiles each line on its own, so a definition has
  to fit on one.
- **Special form names and `#t`/`#f` are reserved.** None of them can be defined, `set!` or
  taken as a parameter, and a special form's name is refused as a value — `(list if)` is a
  syntax error. Quoted, `'if` is the symbol `if` and `'#t` is `#t`.
- **No strings, characters, floats, comments, `begin`, `let`, `cond` or `eq?`.**

## Tests

The compiler tests compile and run real source, one class per area of the language:
`BooleanTest`, `IntegerTest`, `SymbolTest`, `ListTest`, `QuoteTest`, `IfTest`,
`LambdaTest` and `TailCallTest`. Each asserts values through `execute(source, env)`, which compiles, runs and
prints, and errors through `assertSyntaxError`, `assertNameError` and `assertRuntimeError`,
which check the exception type and the exact message. A syntax error is asserted on
compilation alone, so the test also shows it is caught before anything runs.

`TokenizerTest` and `ParserTest` cover the front of the pipeline on their own, including
every malformed input the parser reports. `PrinterTest` covers how each kind of value is
written, `runtime/SymbolTest` that a name has one `Symbol`, and `MainTest` drives the REPL with a scripted standard input: prompts, values,
silence for unspecified results, each kind of error, and recovery after one.
