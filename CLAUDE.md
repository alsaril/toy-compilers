# toy-compilers

Conventions agreed for this repo. `README.md` describes the modules; this file is how to work
on them.

## READMEs

- Describe how things work now. No history or design decisions: no "used to", "no longer",
  "instead of the old …".
- `codegen/README.md` never mentions a front end (`bf`, `math`, `scheme`, "front end",
  "language module"); describe its APIs by their parameters, not by who calls them. The
  `// frontend` and `// backend` comments in `codegen/.../Compiler.kt` are intentional.
- Every sentence in the `codegen` README describes something `codegen` does: what a helper
  registers, emits, checks or refuses. No explaining JVM or JDK semantics. Size a section by
  the `codegen` logic behind it, not by how novel the JVM feature is.
- A front end's README stands alone: no links to, mentions of or comparisons with another
  front end. Linking to `codegen` is fine. The root README is where modules are listed
  together.
- Limits in a README are measured, not estimated.
- `codegen/.../debug/ClassDump.kt` is a local debugging aid: undocumented and untested on
  purpose.

## Code

- No KDoc on small functions whose name and signature already say what they do, in main and
  test code alike. Don't strip comments that are already there.
- An instruction gets into a method only through `+`. Helpers that build an instruction —
  `invokevirtual`, `invokespecial`, `invokestatic`, `invokeinterface`, `invokedynamic` —
  return it for `+` to emit; `constructDefault` is the one helper that emits by itself.

## Tests

- A test sits in the package of the code it tests. Tests of a whole pipeline sit in the
  module's root package (`codegen`'s `GeneratedClassTest`, `CompilerTest`).
- Test a behaviour once, at the layer that owns it: parser errors in `ParserTest`, not again
  through `compile`. A test that adds nothing over an existing one is removed.
- Exceptions are asserted with AssertJ, always with the exact message:
  `assertThatIllegalArgumentException()` / `assertThatIllegalStateException()`, or
  `assertThatExceptionOfType(X::class.java)` for other types, then
  `.isThrownBy { … }.withMessage(…)`. No JUnit `assertThrows`, no bare `assertThatThrownBy`.
- Names are backticked sentences whose verb agrees with the nested group they sit in
  (`Calls` › "make invokevirtual …", `Rejects` › "an unknown option").
- Wording for failures: `codegen` tests say "refuses" and group them under `Refuses`; the
  front ends say "rejects" and group them under `Rejects`.
- A front end's `MainTest` groups its tests as `Evaluates` (or `Runs`), `Reports`,
  `Rejects` and `Help`, plus any group its options need, with `an unknown option` under
  `Rejects`.
- `codegen`'s dynamic forms are tested together in `code/DynamicTest`: `ConstantDynamic` and
  `InvokeDynamic`, each with a `Pool` group and a `Runs` group that loads and runs a class.
- Shared test helpers live in capitalised files (`Fixtures.kt`, `Loading.kt`,
  `Execution.kt`).
- To read what a generated class declares (method names, `max_stack`, `max_locals`,
  `code_length`), use `methodLimits` from `codegen`'s test fixtures
  (`testImplementation(testFixtures(project(":codegen")))`); don't hand-write another
  class-file parser.
- scheme: assert values with `execute(source, env)` and errors with `assertSyntaxError`,
  `assertNameError` and `assertRuntimeError`. A syntax error is asserted on compilation
  alone. Error rows use `@CsvSource(..., delimiter = '|', quoteCharacter = '$')`.

## Reviews

Don't stop at correctness bugs. Also check test coverage and what the tests actually assert
(run `jacocoTestReport`), duplicated or pointless tests, and how a module compares with the
others: a README, an entry in the root README, a CLI with a `MainTest`, and handling of
method size and nesting depth. Confirm a finding by running it; delete any probe afterwards.
