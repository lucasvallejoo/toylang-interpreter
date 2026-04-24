package io.github.lucasvallejoo.toylang.parser

/**
 * Thrown when the parser encounters a token that breaks the grammar.
 *
 * Mirrors [io.github.lucasvallejoo.toylang.lexer.LexerException]: the
 * bare [message] is the diagnostic, and the [line] and [column] are
 * plain properties so the top-level CLI handler can format the
 * location in its own style (caret-under-the-line; see `Main.kt`).
 */
class ParserException(
    message: String,
    val line: Int,
    val column: Int,
) : RuntimeException(message)