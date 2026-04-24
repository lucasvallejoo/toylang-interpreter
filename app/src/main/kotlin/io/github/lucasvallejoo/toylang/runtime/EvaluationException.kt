package io.github.lucasvallejoo.toylang.runtime

/**
 * Thrown by the evaluator when execution cannot continue -- undefined
 * names, type mismatches, division by zero, arity errors, and so on.
 *
 * Mirrors the lexer and parser exceptions: the bare [message] is the
 * diagnostic, the [line] and [column] are plain properties, and the
 * CLI decides how to present them (caret-under-the-line; see `Main.kt`).
 */
class EvaluationException(
    message: String,
    val line: Int,
    val column: Int,
) : RuntimeException(message)