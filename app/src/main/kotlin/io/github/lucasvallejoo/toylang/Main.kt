package io.github.lucasvallejoo.toylang

/**
 * Entry point of the Toylang interpreter.
 *
 * Reads a complete source program from standard input, executes it, and prints
 * the value of every top-level variable to standard output, one per line.
 *
 * The full pipeline (lexer -> parser -> interpreter) will be wired up in the
 * upcoming commits. For now this is a skeleton that only reads stdin so we can
 * verify the build and the end-to-end I/O plumbing work as expected.
 */
fun main() {
    val source = System.`in`.bufferedReader().readText()

    if (source.isBlank()) {
        System.err.println("toylang: no input received on stdin")
        return
    }

    // TODO(phase-1): tokenize -> parse -> evaluate -> print variables
    System.err.println("toylang: read ${source.length} characters from stdin")
    System.err.println("toylang: interpretation not implemented yet")
}