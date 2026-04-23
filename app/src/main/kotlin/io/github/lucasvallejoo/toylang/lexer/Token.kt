package io.github.lucasvallejoo.toylang.lexer

/**
 * Every kind of token the [Lexer] can emit.
 *
 * Tokens are grouped into five conceptual sections:
 *
 *  - Literals:    [NUMBER], [IDENTIFIER], [TRUE], [FALSE]
 *  - Keywords:    [IF], [THEN], [ELSE], [WHILE], [DO], [FUN], [RETURN]
 *  - Operators:   arithmetic ([PLUS]..[PERCENT]),
 *                 comparison ([EQ_EQ]..[GT_EQ]),
 *                 logical    ([AND], [OR], [NOT])
 *  - Punctuation: [EQ], [LPAREN], [RPAREN], [LBRACE], [RBRACE], [COMMA]
 *  - End marker:  [EOF]
 */
enum class TokenType {
    // ---- Literals ----
    NUMBER, IDENTIFIER, TRUE, FALSE,

    // ---- Keywords ----
    IF, THEN, ELSE, WHILE, DO, FUN, RETURN,

    // ---- Arithmetic operators ----
    PLUS, MINUS, STAR, SLASH, PERCENT,

    // ---- Comparison operators ----
    EQ_EQ, BANG_EQ, LT, GT, LT_EQ, GT_EQ,

    // ---- Logical operators (keyword-based: `and`, `or`, `not`) ----
    AND, OR, NOT,

    // ---- Assignment and punctuation ----
    EQ, LPAREN, RPAREN, LBRACE, RBRACE, COMMA,

    // ---- End of input ----
    EOF,
}

/**
 * A single lexical token produced by the [Lexer].
 *
 * @property type    the kind of token (see [TokenType])
 * @property lexeme  the exact slice of source text that produced this token.
 *                   Preserved for error messages and debugging.
 * @property literal the value carried by literal tokens:
 *                    - [NUMBER]     -> [Long] or [Double]
 *                    - [TRUE]/[FALSE] -> [Boolean]
 *                    - [IDENTIFIER] -> [String] (the identifier name)
 *                    - every other token -> `null`
 * @property line    1-indexed line where the token starts (used in errors)
 * @property column  1-indexed column where the token starts (used in errors)
 */
data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any?,
    val line: Int,
    val column: Int,
) {
    override fun toString(): String = buildString {
        append(type)
        append('(')
        append(lexeme)
        append(')')
        if (literal != null) append(" [$literal]")
        append(" @ ")
        append(line)
        append(':')
        append(column)
    }
}