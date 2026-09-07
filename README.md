# toy-compilers

Compilers that emit real JVM class files — no ASM, no runtime interpretation. A shared
bytecode backend plus one language front end per module.

## Modules

| Module | What it is | Docs |
|---|---|---|
| [`codegen`](codegen/README.md) | JVM class file writer: constant pool, stack map frames, a bytecode DSL | [README](codegen/README.md) |
| [`bf`](bf/README.md) | Brainfuck compiler — parses to an instruction tree and generates a `Runnable` class | [README](bf/README.md) |
| `math` | Placeholder for the next front end | — |

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
./gradlew :bf:run --args=path/to/program.bf
```

## CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) builds and tests every module on
push to `main` and on pull requests, and uploads test and coverage reports.
