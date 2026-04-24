package io.github.lucasvallejoo.toylang.parser

/**
 * Thrown when the parser encounters a token that breaks the grammar.
 *
 * Mirrors `LexerException`: the offending [line] and [column] are kept as
 * plain properties so the top-level error handler can format them however
 * it wants (a caret-style visual formatter is planned for a later phase).
 */
class ParserException(
    message: String,
    val line: Int,
    val column: Int,
) : RuntimeException("$message (at line $line, column $column)")
