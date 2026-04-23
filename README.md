# Toylang Interpreter

A tree-walking interpreter for a small imperative language, written in Kotlin.

> **Status:** in early development. This README grows alongside the implementation — every non-trivial design decision is recorded here as it is made.

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

## Project layout

```
.
├── settings.gradle.kts       # Gradle multi-project settings
├── gradle/                   # Gradle wrapper + version catalog
├── app/                      # Interpreter subproject
│   ├── build.gradle.kts      # Build config (JVM 21, Kotlin, JUnit 5)
│   └── src/
│       ├── main/kotlin/...   # Interpreter source
│       └── test/kotlin/...   # Tests
├── examples/                 # Sample programs (to be added)
├── LICENSE
└── README.md
```

## The language

*To be written as the implementation progresses. It will include a short informal description and an EBNF grammar.*

## Architecture

*To be written. High level: a classic lexer → parser → tree-walking interpreter pipeline.*

## Design decisions

*To be written — one subsection per decision, each one starting from the ambiguity it resolves.*

## Beyond the brief

*To be written — features added on top of the minimum requirements, and why each of them felt natural for an interpreter of this kind.*

## Testing

*To be written.*

## Limitations and future work

*To be written.*

## License

MIT. See [LICENSE](LICENSE).