# Toylang Interpreter

A tree-walking interpreter for a small imperative language, written in Kotlin.

> **Status:** lexer implemented; parser and evaluator coming next. This README grows alongside the implementation — every non-trivial design decision is recorded here as it is made.

---

## Background

This project started from a deceptively simple prompt: *"Here are six example programs. Build an interpreter for them."*

No grammar. No formal specification. Just a handful of snippets that hint at the shape of a language without pinning it down. Most of the interesting work lives in that gap — figuring out what the language actually *is* before implementing it, and justifying every choice along the way.

This README is meant to be read linearly, like a short technical story: the ambiguities I found in the brief, the decisions I took, and the reasoning behind each.

## Quick start

**Requirements:** JDK 21 or newer. The Gradle wrapper bundled in the repository will take care of Gradle and — if needed — of downloading a compatible JDK.

Run a program from a file:
```bash
./gradlew run -q < examples/hello.toy
```

Or pipe the source directly:
```bash
echo "x = 2
y = (x + 2) * 2" | ./gradlew run -q
```

*(The `-q` flag silences Gradle's own logging so only the interpreter's output reaches the terminal.)*

At this stage of the project, `run` prints the **token stream** produced by the lexer rather than the final variable values. Parsing and evaluation will replace that output as they are wired up.

## Project layout

```
.
├── settings.gradle.kts          # Gradle multi-project settings
├── gradle/                      # Gradle wrapper + version catalog
├── app/                         # Interpreter subproject
│   ├── build.gradle.kts         # Build config (JVM 21, Kotlin, JUnit 5)
│   └── src/
│       ├── main/kotlin/
│       │   └── io/github/lucasvallejoo/toylang/
│       │       ├── Main.kt      # CLI entry point
│       │       └── lexer/       # Tokenizer
│       └── test/kotlin/
│           └── io/github/lucasvallejoo/toylang/
│               └── lexer/       # Lexer tests
├── examples/                    # Sample programs (to be added)
├── LICENSE
└── README.md
```

## The language

An informal tour of what Toylang currently recognises. A full EBNF grammar will appear here once the parser is in place.

**Values and variables.** Integers (`42`) and floating-point numbers (`3.14`) are both numeric literals. The two keywords `true` and `false` form the boolean type. Variables are introduced and reassigned with `=`.

```
x = 2
pi = 3.14
flag = true
```

**Arithmetic, comparison and logic.** The usual set of operators is available: `+`, `-`, `*`, `/`, `%` for arithmetic; `==`, `!=`, `<`, `<=`, `>`, `>=` for comparison; and the keyword-based `and`, `or`, `not` for logic.

**Control flow.** Branching uses `if cond then ... else ...`; loops use `while cond do ...`.

```
if x > 10 then y = 100 else y = 0
while x < 3 do x = x + 1
```

**Functions and recursion.** Functions are declared with `fun name(args) { body }` and return a value via `return expr`. Recursion is supported.

```
fun fact(n) { if n <= 0 then return 1 else return n * fact(n - 1) }
```

**Comments.** Line comments start with `//` and run to the end of the line.

**Output.** When a program finishes, the interpreter prints every top-level variable with its final value, one per line, in the order in which the variables were first defined. Function-local variables are not printed.

## Architecture

The interpreter is a classic three-stage pipeline:

```
    source text
        │
        ▼
    ┌─────────┐  tokens  ┌────────┐  AST   ┌─────────────┐  values
    │  Lexer  ├─────────▶│ Parser ├───────▶│ Interpreter ├──────────▶ stdout
    └─────────┘          └────────┘        └─────────────┘
```

Each stage lives in its own sub-package so that the code shape mirrors the conceptual shape of the problem.

### Lexer (`toylang.lexer`)

A single-pass, hand-written scanner that turns the raw source into a flat list of tokens. Key characteristics:

- **Position-aware.** Every token carries the 1-indexed line and column where it started, so every stage downstream can point at the exact spot when it needs to complain.
- **Typed literals.** Numbers are parsed once, here: an integer literal produces a `Long`, a literal with a decimal point produces a `Double`. The parser and the interpreter never see raw number strings.
- **Keyword-table lookup.** Identifiers and keywords share the same scanning path. Once the lexer has read an identifier, a single map lookup decides whether it is a keyword (e.g. `if` → `TokenType.IF`) or a plain identifier. This keeps the code for both identical and avoids duplicating case logic per keyword.
- **Whitespace-transparent.** Spaces, tabs, carriage returns and newlines are all pure whitespace — they update line/column counters but never produce tokens. Statement boundaries are the parser's concern.

Parser and evaluator will be documented here as they land.

## Design decisions

Each ambiguity left open by the task is resolved here, together with the reasoning behind the choice.

### Comments: `//` for line comments

The sample programs don't use any, but a language without comments is unusual, and the mentor's brief encourages features that feel natural. `//` was picked over `#` for familiarity — it matches Java, Kotlin, C, and most of the languages the reviewer will have in muscle memory.

### Numeric types: integers and doubles

The task only shows integer literals, but supporting `Double` is a small, natural extension that signals a deliberate take on the type system. A literal without a `.` is a `Long`; a literal with a `.` followed by at least one digit is a `Double`. The interpreter (phase 3) will apply standard numeric promotion: if either operand is a `Double`, the result is a `Double`; otherwise both are `Long` and the result stays `Long`. That mirrors what Java and Kotlin do and removes surprises.

Leading dots (`.5`) are rejected on purpose — every number must have at least one digit before the dot. That matches Java and Go, and it leaves room for a future method-call syntax (`1.abs()`) without ambiguity.

### Logical operators as keywords (`and`, `or`, `not`)

The language already uses English keywords for everything control-flow-related (`if`, `then`, `do`, `return`). Using the words `and`, `or`, `not` for logic keeps the tone consistent. `&&`, `||`, `!` would feel out of place.

### Identifier rule: `[A-Za-z_][A-Za-z0-9_]*`

The standard rule: an identifier starts with a letter or an underscore and continues with any letter, digit, or underscore. This is what the sample programs use (`fact_rec`, `x`, `y`) and matches identifier rules in most curly-brace languages.

### Newlines are pure whitespace; there is no explicit statement terminator

The sample programs happily mix newlines and commas as separators. Rather than adopt JavaScript-style automatic semicolon insertion, the lexer treats every whitespace character (including `\n`) as insignificant, and the parser (phase 2) will rely on *greedy parsing* to find statement boundaries: it consumes as much as it can of the current statement, and when it encounters a token that cannot extend the current construct it starts a new statement. This is simpler to implement, simpler to explain, and works on every sample in the brief.

### Errors include line and column, but no caret yet

The lexer throws a `LexerException` carrying the line and column of the offending character. A richer, caret-under-the-line visual presentation is planned for the final polish phase — intentionally out of the MVP — but the positional information is already captured, so it is a drop-in upgrade later.

## Beyond the brief

Features that are not strictly required but felt natural enough for a real interpreter to include from the start:

- **Floating-point literals.** The brief only shows integers; doubles cost almost nothing extra and make the numeric story complete.
- **Line comments.** Not strictly required, but unnatural to omit in a language that anyone would actually try to use.
- **Line/column tracking on every token.** Enables meaningful error messages at every stage — now, for the lexer, and automatically in the future for the parser and the evaluator.
- **Logical operators (`and`, `or`, `not`).** The samples don't use them but any non-trivial program soon does.

Further stretch goals — REPL mode, a `--debug` flag that dumps the AST, caret-based error presentation, a suite of runnable example programs — are captured in *Limitations and future work* and will be picked up as time allows.

## Testing

Unit tests live under `app/src/test/kotlin` and run with:

```bash
./gradlew test
```

The lexer is covered by a focused suite (`LexerTest`) that exercises literals (including the integer/double split), identifiers, keywords, operators (both single- and two-character forms), comments, whitespace handling, error cases, and a pair of smoke tests against real programs from the brief.

## Limitations and future work

- **Parser and evaluator.** Not yet implemented. Currently `./gradlew run` prints the token stream rather than executing the program.
- **No REPL.** The interpreter reads a single program from stdin and exits. An interactive mode would be a nice-to-have.
- **Plain-text error messages.** A caret-based visual formatter would be a much nicer experience and is already enabled by the position information the lexer records.
- **Old-Mac line endings.** CRLF (`\r\n`) works fine because the `\n` resets line counters, but a file using bare `\r` as the line terminator would keep counting on the same line. Not a realistic scenario today, but worth flagging.

## License

MIT. See [LICENSE](LICENSE).
