# toy-compilers

Compilers that emit real JVM class files — no ASM, no runtime interpretation. A shared
bytecode backend plus one language front end per module.

## Modules

| Module | What it is | Docs |
|---|---|---|
| [`codegen`](codegen/README.md) | JVM class file writer: constant pool, exception handlers, a bytecode DSL, and an analysis that type-checks each method like the JVM verifier and derives `max_stack`, `max_locals` and the stack map frames | [README](codegen/README.md) |
| [`bf`](bf/README.md) | Brainfuck compiler — parses to an instruction tree and generates a `Program` class | [README](bf/README.md) |
| [`math`](math/README.md) | Arithmetic expression compiler — parses to an expression tree and generates a `Program` class | [README](math/README.md) |
| [`scheme`](scheme/README.md) | Scheme subset behind a REPL — compiles each line to a `Program` class and every lambda to a class of its own | [README](scheme/README.md) |

`codegen` knows nothing about any source language; a front end depends on it and does
nothing but build class files.

## Build

Kotlin, JDK 21, Gradle wrapper. Generated classes target Java 8 (major version 52).

```bash
./gradlew build            # compile and test everything
./gradlew :codegen:test    # one module
```

Run a Brainfuck program:

```bash
./gradlew :bf:run --args="--memsize=65536 --cycles=1000000 path/to/program.bf"
```

Evaluate an expression:

```bash
./gradlew :math:installDist
math/build/install/math/bin/math --expr "x*y+1" --vars "x=2;y=3"
```

Start a Scheme REPL:

```bash
./gradlew :scheme:installDist
scheme/build/install/scheme/bin/scheme
```

## CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) builds and tests every module on
push to `main` and on pull requests, and uploads test and coverage reports.
