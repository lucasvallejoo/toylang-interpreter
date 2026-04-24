package io.github.lucasvallejoo.toylang.runtime

/**
 * Thrown when the evaluator encounters a program that is syntactically
 * valid but cannot be executed: a division by zero, an undefined
 * variable, a type mismatch, a call with the wrong number of arguments,
 * and so on.
 *
 * Mirrors [io.github.lucasvallejoo.toylang.lexer.LexerException] and
 * [io.github.lucasvallejoo.toylang.parser.ParserException]: the same
 * shape (message + line + column) so the top-level error handler in
 * `Main.kt` can format all three uniformly, with one clear class per
 * pipeline stage so the user (and the reviewer) sees exactly where the
 * program broke.
 */
class EvaluationException(
    message: String,
    val line: Int,
    val column: Int,
) : RuntimeException("$message (at line $line, column $column)") 