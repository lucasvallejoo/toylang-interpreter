# Toylang Interpreter

A tree-walking interpreter for a small imperative language, written in Kotlin.

> **Status:** complete — lexer, parser, and evaluator all implemented and tested.

---

## Background

This project started from a deceptively simple prompt: *"Here are six example programs. Build an interpreter for them."*

No grammar. No formal specification. Just a handful of snippets that hint at the shape of a language without pinning it down. Most of the interesting work lives in that gap — figuring out what the language actually *is* before implementing it, and justifying every choice along the way.

This README is meant to be read linearly, like a short technical story: the ambiguities I found in the brief, the decisions I took, and the reasoning behind each.

## Quick start

**Requirements:** JDK 21 or newer. The Gradle wrapper bundled in the repository will take care of Gradle and — if needed — of downloading a compatible JDK.

The interpreter has three modes:

```bash
# Run a program from a file (most common)
./gradlew run -q --args="examples/hello.toy"

# Or pipe source through stdin
echo "x = 2
y = (x + 2) * 2" | ./gradlew run -q

# Drop into the interactive REPL
./gradlew run -q --args="--repl"
```

*(The `-q` flag silences Gradle's own logging; `--args=` is Gradle's way of forwarding arguments to the program. Run `--args="--help"` for the full usage banner.)*

The interpreter prints the final value of every top-level variable, one per line, in the order in which they were first assigned:

```
x = 2
y = 8
```

If the program contains a runtime error (division by zero, undefined variable, type mismatch, etc.) the interpreter writes a diagnostic to **standard error** and exits with a non-zero status, so the standard output stays clean. Diagnostics include a caret pointing at the offending character; when running from a file, the location uses the editor-friendly `path:line:col` format:

```
toylang: undefined variable 'y'
  at examples/buggy.toy:3:5

    x = y + 1
        ^
```

To inspect the parse tree without running the program, pass `--debug`:
```bash
./gradlew run -q --args="--debug examples/06_recursion.toy"
```
The AST is dumped (as s-expressions) to standard error before execution; standard output stays clean, so redirecting `>` still captures only the program's final state.

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
│       │       ├── lexer/       # Tokenizer
│       │       ├── ast/         # AST nodes + pretty-printer
│       │       ├── parser/      # Recursive-descent parser
│       │       └── runtime/     # Evaluator, environment, runtime values
│       └── test/kotlin/
│           └── io/github/lucasvallejoo/toylang/
│               ├── lexer/       # Lexer tests
│               ├── parser/      # Parser tests
│               └── runtime/     # Evaluator tests
├── examples/                    # Runnable Toylang programs (brief samples 1/3/6 + 3 demos + hello)
├── LICENSE
└── README.md
```

## The language

An informal tour of what Toylang accepts. A formal grammar follows in the next section.

**Values and variables.** Integers (`42`) and floating-point numbers (`3.14`) are both numeric literals. The two keywords `true` and `false` form the boolean type. Strings are double-quoted (`"hello"`) and support the escape sequences `\n`, `\t`, `\"`, `\\`. Variables are introduced and reassigned with `=`.

```
x = 2
pi = 3.14
flag = true
greeting = "hello"
```

**Arithmetic, comparison and logic.** The usual set of operators is available: `+`, `-`, `*`, `/`, `%`, `**` for arithmetic (the last is exponentiation, right-associative); `==`, `!=`, `<`, `<=`, `>`, `>=` for comparison; and the keyword-based `and`, `or`, `not` for logic. The `+` operator is overloaded for strings: when both sides are strings, it concatenates.

**Control flow.** Branching uses `if cond then ... else ...`; loops use `while cond do ...`.

```
if x > 10 then y = 100 else y = 0
while x < 3 do x = x + 1
```

**Functions and recursion.** Functions are declared with `fun name(args) { body }` and return a value via `return expr`. Recursion is supported.

```
fun fact(n) { if n <= 0 then return 1 else return n * fact(n - 1) }
```

**Built-in `print`.** A single built-in function emits a line to standard output during execution. It accepts any number of arguments and joins them with spaces; strings are written without their surrounding quotes:

```
print("the answer is", 6 * 7)   // -> the answer is 42
```

A user-defined `fun print` shadows the built-in for the rest of the program, so the name is never reserved at the language level.

**Comments.** Line comments start with `//` and run to the end of the line.

**Output.** When a program finishes, the interpreter prints every top-level variable with its final value, one per line, in the order in which the variables were first defined. Function names and function-local variables are not printed. Strings appear in quoted form (`s = "hello"`) so they cannot be confused with identifiers.

## Grammar

```ebnf
program      = { top_stmt } ;
top_stmt     = fun_decl | simple_stmt ;
fun_decl     = "fun" IDENT "(" [ param_list ] ")" "{" [ body_seq ] "}" ;
param_list   = IDENT { "," IDENT } ;

simple_stmt  = assignment
             | if_stmt
             | while_stmt
             | return_stmt
             | expr_stmt ;
assignment   = IDENT "=" expression ;
if_stmt      = "if" expression "then" simple_stmt "else" simple_stmt ;
while_stmt   = "while" expression "do" body_seq ;
return_stmt  = "return" expression ;
expr_stmt    = expression ;
body_seq     = simple_stmt { "," simple_stmt } ;  (* at least one stmt *)

expression   = or_expr ;
or_expr      = and_expr   { "or" and_expr } ;
and_expr     = not_expr   { "and" not_expr } ;
not_expr     = "not" not_expr | equality ;
equality     = comparison { ( "==" | "!=" ) comparison } ;
comparison   = term       { ( "<" | "<=" | ">" | ">=" ) term } ;
term         = factor     { ( "+" | "-" ) factor } ;
factor       = unary      { ( "*" | "/" | "%" ) unary } ;
unary        = "-" unary | power ;
power        = call [ "**" unary ] ;       (* right-assoc; right side re-enters unary *)
call         = IDENT "(" [ arg_list ] ")" | primary ;
arg_list     = expression { "," expression } ;
primary      = NUMBER | STRING | BOOL | IDENT | "(" expression ")" ;
```

Top-level statements are *not* separated by any explicit token — the parser uses greedy parsing to find their boundaries. Inside a `while ... do` body or a `fun ... { }` body, statements are separated by commas, which matches the syntax used in sample 3 of the brief.

## Architecture

The interpreter is a classic three-stage pipeline:

```
    source text
        │
        ▼
    ┌─────────┐  tokens  ┌────────┐  AST   ┌───────────┐  values
    │  Lexer  ├─────────▶│ Parser ├───────▶│ Evaluator ├──────────▶ stdout
    └─────────┘          └────────┘        └───────────┘
```

Each stage lives in its own sub-package so that the code shape mirrors the conceptual shape of the problem.

### Lexer (`toylang.lexer`)

A single-pass, hand-written scanner that turns the raw source into a flat list of tokens. Key characteristics:

- **Position-aware.** Every token carries the 1-indexed line and column where it started, so every stage downstream can point at the exact spot when it needs to complain.
- **Typed literals.** Numbers are parsed once, here: an integer literal produces a `Long`, a literal with a decimal point produces a `Double`. The parser and the interpreter never see raw number strings.
- **Keyword-table lookup.** Identifiers and keywords share the same scanning path. Once the lexer has read an identifier, a single map lookup decides whether it is a keyword (e.g. `if` → `TokenType.IF`) or a plain identifier. This keeps the code for both identical and avoids duplicating case logic per keyword.
- **Whitespace-transparent.** Spaces, tabs, carriage returns and newlines are all pure whitespace — they update line/column counters but never produce tokens. Statement boundaries are the parser's concern.

### AST (`toylang.ast`)

The output of the parser is a tree of sealed Kotlin classes split along the usual line:

- **`Expr`** — `NumberLit`, `BoolLit`, `StringLit`, `VarRef`, `Binary`, `Unary`, `Call`. Operators are enums (`BinaryOp`, `UnaryOp`) so that the parser cannot accidentally emit an unknown operator symbol.
- **`Stmt`** — `Assignment`, `If`, `While`, `Return`, `FunDecl`, `ExprStmt`.
- **`Program`** — a thin wrapper around `List<Stmt>`.

Every node carries the `line`/`column` of the token that produced it, so the evaluator can blame the right source location at runtime. An s-expression pretty-printer (`sexpr()`) is provided for debugging.

### Parser (`toylang.parser`)

A hand-written recursive-descent parser with **precedence climbing** for expressions. Each level of the precedence table maps to one method, and tighter-binding levels are called from inside looser ones. Writing it by hand rather than generating one has two upsides: the precedence table is directly visible in the code, and error messages stay fully under our control.

| Level | Operators                       | Associativity |
|-------|---------------------------------|---------------|
| 1     | `or`                            | left          |
| 2     | `and`                           | left          |
| 3     | `not` (unary)                   | right         |
| 4     | `==` `!=`                       | left          |
| 5     | `<` `<=` `>` `>=`               | left          |
| 6     | `+` `-`                         | left          |
| 7     | `*` `/` `%`                     | left          |
| 8     | unary `-`                       | right         |
| 9     | `**`                            | right         |
| 10    | function call                   | n/a           |
| 11    | literals, identifiers, grouping | n/a           |

On a grammar error, the parser raises a `ParserException` carrying the exact line and column of the offending token and a message of the shape *"Expected X, found Y"*. No error recovery is attempted — in an MVP interpreter, the first error is the one that matters.

### Evaluator (`toylang.runtime`)

A **tree-walking interpreter**: it recurses over the AST produced by the parser and computes values directly, with no compilation or bytecode generation step. The runtime sub-package contains three supporting classes:

- **`Value`** — a sealed hierarchy of runtime values: `LongVal`, `DoubleVal`, `BoolVal`, `StringVal`, `FunVal`. Each value renders itself two ways: `display()` for the end-of-program top-level dump (strings are quoted) and `asText()` for the `print` built-in (strings are bare).
- **`Environment`** — storage for variables (a `LinkedHashMap` at the top level to preserve insertion order, plus a stack of local frames for function calls) and for function declarations (a separate map, since the two namespaces are independent).
- **`EvaluationException`** — mirrors `LexerException` and `ParserException`; carries a message and a source position for clean CLI diagnostics.

The evaluator itself (`Evaluator`) exposes a single public method: `run(program)`, which returns the `Environment` when execution finishes. The `Main.kt` entry point then iterates over `environment.topLevelVariables()` to print the final state. Function calls are resolved **user-first, built-ins second**: a `fun print(...)` declaration in the source transparently shadows the language's own `print`.

## Design decisions

Each ambiguity left open by the task is resolved here, together with the reasoning behind the choice. The decisions are grouped by the phase in which they were taken.

### Lexer

#### Comments: `//` for line comments

The sample programs don't use any, but a language without comments is unusual, and the mentor's brief encourages features that feel natural. `//` was picked over `#` for familiarity — it matches Java, Kotlin, C, and most of the languages the reviewer will have in muscle memory.

#### Numeric types: integers and doubles

The task only shows integer literals, but supporting `Double` is a small, natural extension that signals a deliberate take on the type system. A literal without a `.` is a `Long`; a literal with a `.` followed by at least one digit is a `Double`. The interpreter applies standard numeric promotion: if either operand is a `Double`, the result is a `Double`; otherwise both are `Long` and the result stays `Long`. That mirrors what Java and Kotlin do and removes surprises.

Leading dots (`.5`) are rejected on purpose — every number must have at least one digit before the dot. That matches Java and Go, and it leaves room for a future method-call syntax (`1.abs()`) without ambiguity.

#### Logical operators as keywords (`and`, `or`, `not`)

The language already uses English keywords for everything control-flow-related (`if`, `then`, `do`, `return`). Using the words `and`, `or`, `not` for logic keeps the tone consistent. `&&`, `||`, `!` would feel out of place.

#### Identifier rule: `[A-Za-z_][A-Za-z0-9_]*`

The standard rule: an identifier starts with a letter or an underscore and continues with any letter, digit, or underscore. This is what the sample programs use (`fact_rec`, `x`, `y`) and matches identifier rules in most curly-brace languages.

#### Newlines are pure whitespace; there is no explicit statement terminator

The sample programs happily mix newlines and commas as separators. Rather than adopt JavaScript-style automatic semicolon insertion, the lexer treats every whitespace character (including `\n`) as insignificant, and the parser uses *greedy parsing* to find statement boundaries: each statement consumes as much as it can, and the next statement starts at whatever is left over. This is simpler to implement, simpler to explain, and works on every sample in the brief.

#### Errors include line and column, but no caret yet

The lexer throws a `LexerException` carrying the line and column of the offending character. A richer, caret-under-the-line visual presentation is planned for the final polish phase — intentionally out of the MVP — but the positional information is already captured, so it is a drop-in upgrade later.

### Parser

#### `else` is mandatory

Every `if` must come with an `else`. Making the `else` optional would reintroduce the classic *dangling-else* ambiguity (does `else` attach to the closest or the furthest open `if`?), which is pointless complexity when the problem vanishes entirely by requiring an `else` branch. The cost — having to write `else y = y` when you really meant "do nothing" — is tiny and the gain in grammar simplicity is real.

#### Both branches of `if` are a single simple statement

An if-branch is one statement, not a braced block. The sample programs use this shape directly (`if x == 1 then y = 10 else y = y + 1`), and combined with the mandatory `else` it gives the grammar a pleasingly small footprint. Longer branches can be written as a `while`-wrapped body or moved into a function.

#### Braces `{ }` are only used to delimit function bodies

No `{ ... }` block statement exists. This is a direct consequence of the previous decision, and of the fact that the sample programs never use braces except for function declarations. Introducing a general block-statement would be redundant with the `while ... do body_seq` construct and would create a second way to spell the same thing.

#### Function declarations are top-level only

A `fun` declaration is not a valid simple statement, so it cannot appear inside an `if`, a `while`, or another function. This matches the sample programs (all function declarations are at the top level) and avoids the semantic complexity of nested functions and closures — neither of which is needed to run the brief's examples.

#### `while` body uses a comma-separated body_seq; `if` branches do not

Sample 3 of the brief is the disambiguator:

```
while x < 3 do if x == 1 then y = 10 else y = y + 1, x = x + 1
```

The expected output `x=3, y=11` is only reachable if the trailing `, x = x + 1` is part of the `while` body, not of the `else` branch. The grammar therefore uses different rules for the two constructs: `if` branches are a single simple statement (so the comma stops them), while `while` bodies are a `body_seq` — a comma-separated list that can contain several statements. It also matches how `fun` bodies work: commas separate statements inside `{ ... }` too.

#### Assignment is a statement, not an expression

`a = b = 0` does not parse. Keeping `=` strictly at statement level makes expressions pure (no hidden side effects) and makes the grammar a clean split between *things that produce a value* and *things that do something*. The sample programs never chain assignments, so the only cost is an ergonomic feature that is not asked for.

#### Comparison chains parse but do not carry a meaning

`a < b < c` parses as `(a < b) < c` (left-associative, same as the other comparison operators). The parser does not reject it; the evaluator will raise a type error at runtime when the left side evaluates to a boolean and `<` demands a number. Documenting the trap here is cheaper than introducing a special rule.

#### `not` sits below comparison in the precedence table

`not a == b` parses as `not (a == b)`, matching Python and Kotlin. Arithmetic unary `-` is kept tight (so that `-2 * 3` is `(-2) * 3`), but `not` being a keyword makes it read naturally as a predicate modifier rather than as a bit flip. Placing it just below the comparison level captures that intuition.

### Evaluator

#### Two-pass execution for forward references

`run(program)` makes two passes: first it registers every top-level `fun` declaration, then it executes the remaining statements. This lets a function call another declared further down in the file — the expected behaviour in virtually every language — without requiring any lookahead in the parser or a separate linking step.

#### Integer division is preserved; numeric promotion is opt-in

If both operands of an arithmetic operation are `Long`, the result is `Long`: `7 / 2` yields `3`. Promotion to `Double` only happens when at least one operand is already a `Double` (`7.0 / 2` yields `3.5`). This mirrors Java, Kotlin, and C, and avoids the surprise where a stray float literal somewhere in the program silently changes the type of every computation around it.

#### Short-circuit evaluation for `and` and `or`

`false and expr` never evaluates `expr`; `true or expr` never evaluates `expr`. Both operators still require a boolean on each side — there is no truthy/falsy coercion. Short-circuit matters in practice: `x != 0 and 10 / x > 1` is safe even when `x` is zero.

#### Cross-type equality is well-defined, not an error

`1 == 1.0` is `true` (numeric comparison after promotion). `1 == true` is `false` — not a runtime error. Mixing a number with a boolean can never be equal, but it is also not worth aborting the program over. This policy avoids a common pitfall in strictly-typed languages where simple guards like `if result == false` fail to compile after a refactor changes the type of `result`.

#### `return` is implemented as a control-flow exception (`ReturnSignal`)

A `return` statement throws a private `ReturnSignal` exception that is caught exactly at the call site in `evalCall`. This lets `return` unwind from any depth of nesting — inside an `if`, inside a `while`, anywhere — without threading a flag through every level of the recursive walk. The stack trace is suppressed because filling it on every `return` would be pure overhead with no diagnostic value.

#### Every function must return a value; there is no `void`

If execution reaches the end of a function body without hitting a `return`, the evaluator raises a runtime error. Toylang has no `unit` or `void` type — every call must produce a value. This is strict, but it makes the language's semantics uniform: an expression involving a function call always has a type, and you never have to wonder whether a call site will silently produce `null`.

#### Python-like scoping: functions read globals, writes create locals

Inside a function body, reading a variable that is not local falls back to the global scope. Writing always targets the local frame, even if a global with the same name exists. This is Python's default scoping rule without `global` / `nonlocal`: it is simple to explain and sufficient for every pattern in the brief's sample programs.

### Strings, `print`, and power

#### String concatenation, no implicit coercion

`"a" + "b"` is `"ab"`; `"a" + 1` is a runtime type error, **not** `"a1"`. Implicit coercion in `+` is one of the most common silent-bug sources in dynamically-typed languages (`"" + obj` accidentally calling `toString` on the wrong thing); requiring an explicit conversion costs little and removes that whole class of mistakes. When a `str()` built-in lands, the conversion will be visible in the source.

#### Strings are quoted in the top-level dump but bare in `print`

A top-level variable holding `"hello"` shows up as `s = "hello"` in the final dump, with quotes and re-escaped specials. The `print` built-in, on the other hand, emits the bare characters: `print("hello")` outputs `hello`. This is the same split Python makes between `repr()` and `str()`, exposed via two methods on `Value`: `display()` (used by the dump) and `asText()` (used by `print`). The two contracts are different and deserve different methods.

#### Raw newlines inside a string literal are rejected

Multi-line literals are deliberately not supported. The far more common case is a forgotten closing quote, and erroring at the first newline produces a clean *"unterminated string"* diagnostic instead of swallowing arbitrary source until the next `"` shows up many lines later. Newline characters are still expressible via the `\n` escape, so nothing is actually lost.

#### Built-ins are resolved as a fallback after user functions

`evalCall` looks up the user environment first; only on a miss does it consult a small built-in registry (currently just `print`). The order is the predictable one — what is yours in the source is yours at runtime — and it makes shadowing work transparently: `fun print(x) { ... }` simply takes precedence, no special syntax needed.

#### `print` returns `0`, accepts arity zero

Toylang has no `void` / `unit`, so every call must produce a value. `print` returns `LongVal(0)` by convention, matching C's `printf`; in practice almost every call discards the result by using `print` as a bare statement. Arity is unrestricted: `print()` emits just a newline, `print(a, b, c)` writes them space-separated.

#### Power: `**` chosen over `^`, with Python's asymmetric precedence

`^` reads as XOR even in languages that don't have XOR — too much false intuition. `**` is unambiguous and follows the lineage of Python and Ruby. The operator is right-associative (`2 ** 3 ** 2 == 512`) and tighter than unary `-` on the left but looser on the right: `-2 ** 2` is `-(2 ** 2) = -4`, while `2 ** -3` is `2 ** (-3) = 0.125`. The asymmetry is implemented by having the right operand re-enter `parseUnary`; it matches Python and is what most people intuit when they see `**`.

#### Integer power stays integer when honest, falls back to `Double`

`2 ** 10` is `1024` (Long); `2 ** -1` is `0.5` (Double). Whenever both operands are Long *and* the exponent is non-negative we stay in integer arithmetic via repeated squaring (`O(log e)` multiplications) — using `Math.pow` instead would silently lose precision past `2^53`. Negative exponents and any Double operand promote to Double and use `Math.pow`, since there is no way to honestly express `2 ** -1` as a Long.

### CLI and REPL

#### REPL multi-line via "found EOF", not bracket counting

Inside the REPL, every line is appended to a buffer and the cumulative buffer is sent through the lexer and parser. When the parser fails with a message containing `"found EOF"`, the input is treated as incomplete and the prompt switches to `... ` so the user can finish typing on the next line. This works for any partial syntax — open paren, open brace, dangling operator — *because the parser already knows when it has run out of input*. The alternative (manually counting brackets in the REPL) would duplicate logic that already exists in `Parser.expect`. Re-using its diagnostic instead of reimplementing the rule is what makes the multi-line behaviour transparent.

#### CLI exit codes follow Unix conventions

`0` for success (or `--help`), `1` for program errors (lexer / parser / runtime / I/O), `2` for usage errors (unknown flag, conflicting flags, too many file arguments). The split lets shell scripts react differently to "your program is broken" versus "you typed the command wrong". `--help` writes to standard output (it is requested information, not an error), every other diagnostic writes to standard error (so redirecting `>` keeps the captured output clean even when something goes wrong).

#### Diagnostics carry the filename when one is known

When the source comes from a file, errors are formatted as `at path/to/file.toy:line:col` — the convention recognised by IDEs and editors so jumping to the location is one click. When the source comes from stdin or the REPL there is no file to mention, so the diagnostic falls back to `at line N, column M`.

## Beyond the brief

Features that are not strictly required but felt natural enough for a real interpreter to include from the start:

- **Floating-point literals.** The brief only shows integers; doubles cost almost nothing extra and make the numeric story complete.
- **Line comments.** Not strictly required, but unnatural to omit in a language that anyone would actually try to use.
- **Line/column tracking on every token, AST node, and runtime error.** Enables meaningful error messages at every stage.
- **Logical operators (`and`, `or`, `not`).** The samples don't use them but any non-trivial program soon does.
- **S-expression pretty-printer for the AST.** Turns the parse tree into a readable Lisp-style dump, which is invaluable for debugging and for showing how precedence actually nests.
- **Short-circuit evaluation.** Not required by any sample program, but essential for safe guard expressions like `x != 0 and 10 / x > 1`.
- **Caret-under-the-line error messages.** Every diagnostic — lexer, parser, or runtime — is rendered with the offending source line and a `^` under the exact column, in the style of modern compilers.
- **`--debug` CLI flag.** Dumps the AST as s-expressions to standard error before execution, so the three phases of the pipeline are individually inspectable without opening the source.
- **Runnable examples directory.** Every program from the brief is available as a standalone `.toy` file under `examples/`, with an expected-output comment at the top. Handy for smoke-testing after a change.
- **String literals.** Double-quoted, with the canonical escapes `\n \t \" \\`. Concatenation via `+` (only when both sides are strings — no implicit coercion).
- **`print` built-in.** A single first-class side-effect function that lets programs emit output during execution. Multi-arg, space-separated, newline-terminated; user-defined functions of the same name shadow it transparently.
- **Power operator (`**`).** Right-associative, with Python's asymmetric precedence around unary `-`. Stays in integer arithmetic when honest; promotes to `Double` only when negative exponents or non-integer operands force it.
- **Interactive REPL (`--repl`).** A read-eval-print loop with persistent state, multi-line buffering (driven by the parser's "found EOF" signal — no manual brace-counting), and meta-commands `:q` to quit and `:dump` to list every bound variable.
- **File argument (`--args="path/to/program.toy"`).** Programs can be invoked by path instead of stdin redirection; diagnostics use the editor-friendly `file:line:col` format so an IDE jumps straight to the offending location.

## Testing

Unit tests live under `app/src/test/kotlin` and run with:

```bash
./gradlew test
```

Three suites cover all three implemented phases:

- **`LexerTest`** exercises literals (the integer/double split, string escapes, unterminated strings, invalid escapes), identifiers, keywords, operators (single- and two-character forms, including `**`), comments, whitespace handling, error cases, and smoke tests against real programs from the brief.
- **`ParserTest`** covers operator precedence at every level (arithmetic, comparison, logical, unary, power with its right-associativity and Python-style asymmetry around unary `-`), associativity, statement forms (assignment, if/else, while, return, function declaration, call, bare expression), string literals (concat, equality, expression-statement), error cases (missing `then`, missing `else`, unclosed parens, nested functions, chained assignment), and the three brief samples that most stress the grammar.
- **`EvaluatorTest`** covers arithmetic (integer vs. float promotion, division truncation, modulo, operator precedence, integer and Double power), boolean logic (short-circuit, strict type checks), control flow (if/else, while, nested loops), scoping (globals readable but not writable from functions), recursion, strings (concatenation, equality, cross-type comparisons, type errors, display vs. `asText` rendering), the `print` built-in (multi-arg, no-arg, mixed types, user-shadowing, integration with loops and functions), every documented runtime error case, and end-to-end execution of the brief's three pinned-down samples (1, 3, 6) plus three additional demos for the language features they did not stress.

## Limitations and future work

- **Old-Mac line endings.** CRLF (`\r\n`) works fine because the `\n` resets line counters, but a file using bare `\r` as the line terminator would keep counting on the same line. Not a realistic scenario today, but worth flagging.
- **Comparison chains.** `a < b < c` parses as `(a < b) < c`. The right-hand comparison will receive a boolean from the left-hand result and raise a type error at runtime. This is a reasonable outcome but not a friendly diagnostic; a dedicated "chained comparison" error message would be clearer.
- **No error recovery in the parser.** The first grammar error aborts parsing. This is adequate for an MVP but a production-grade interpreter would attempt to continue after the first error to report multiple issues at once.
- **Strings cannot be ordered.** `==` and `!=` work on strings, but `<`, `<=`, `>`, `>=` reject them. Lexicographic comparison would be a small extension; left out because no sample uses it.
- **No multi-line strings.** A raw newline inside a literal is treated as an unterminated-string error. Adding a triple-quoted form (`"""..."""`) would be the natural escape hatch.
- **No string interpolation, no `str()` built-in.** Mixing strings with numbers in `+` raises a type error. Until an explicit conversion is exposed, programs must build output manually with `print(label, number)` instead of `print("count = " + n)`.
- **Power overflow wraps silently.** `2 ** 63` produces `Long.MIN_VALUE` rather than a runtime error. Detecting overflow on every multiplication is a small cost that would be reasonable to add.

## License

MIT. See [LICENSE](LICENSE).