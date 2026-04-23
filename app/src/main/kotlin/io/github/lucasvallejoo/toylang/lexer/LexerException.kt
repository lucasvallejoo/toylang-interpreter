package io.github.lucasvallejoo.toylang.lexer

/**
 * Thrown by [Lexer] whenever the source contains a character sequence that
 * cannot be classified into any known token.
 *
 * The message produced by this exception is deliberately concise and always
 * includes the source location, so it can be presented to the end user as is
 * without further formatting. A richer, caret-based error presentation is
 * intentionally left out of the minimum implementation; it is documented as
 * future work in the project README.
 *
 * @property line   1-indexed line where the offending input starts
 * @property column 1-indexed column where the offending input starts
 */
class LexerException(
    message: String,
    val line: Int,
    val column: Int,
) : RuntimeException("$message (at line $line, column $column)")
