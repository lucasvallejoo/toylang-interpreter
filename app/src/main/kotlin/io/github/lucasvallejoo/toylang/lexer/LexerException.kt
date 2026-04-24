package io.github.lucasvallejoo.toylang.lexer

/**
 * Thrown by [Lexer] whenever the source contains a character sequence that
 * cannot be classified into any known token.
 *
 * The bare [message] stays separate from the location: the [line] and
 * [column] live as plain properties so the top-level CLI handler can
 * render them however it chooses (currently a caret-under-the-line
 * diagnostic; see `Main.kt`).
 *
 * @property line   1-indexed line where the offending input starts
 * @property column 1-indexed column where the offending input starts
 */
class LexerException(
    message: String,
    val line: Int,
    val column: Int,
) : RuntimeException(message)