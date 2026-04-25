package io.github.lucasvallejoo.toylang.lexer

import io.github.lucasvallejoo.toylang.lexer.TokenType.AND
import io.github.lucasvallejoo.toylang.lexer.TokenType.BANG_EQ
import io.github.lucasvallejoo.toylang.lexer.TokenType.COMMA
import io.github.lucasvallejoo.toylang.lexer.TokenType.DO
import io.github.lucasvallejoo.toylang.lexer.TokenType.ELSE
import io.github.lucasvallejoo.toylang.lexer.TokenType.EOF
import io.github.lucasvallejoo.toylang.lexer.TokenType.EQ
import io.github.lucasvallejoo.toylang.lexer.TokenType.EQ_EQ
import io.github.lucasvallejoo.toylang.lexer.TokenType.FALSE
import io.github.lucasvallejoo.toylang.lexer.TokenType.FUN
import io.github.lucasvallejoo.toylang.lexer.TokenType.GT
import io.github.lucasvallejoo.toylang.lexer.TokenType.GT_EQ
import io.github.lucasvallejoo.toylang.lexer.TokenType.IDENTIFIER
import io.github.lucasvallejoo.toylang.lexer.TokenType.IF
import io.github.lucasvallejoo.toylang.lexer.TokenType.LBRACE
import io.github.lucasvallejoo.toylang.lexer.TokenType.LPAREN
import io.github.lucasvallejoo.toylang.lexer.TokenType.LT
import io.github.lucasvallejoo.toylang.lexer.TokenType.LT_EQ
import io.github.lucasvallejoo.toylang.lexer.TokenType.MINUS
import io.github.lucasvallejoo.toylang.lexer.TokenType.NOT
import io.github.lucasvallejoo.toylang.lexer.TokenType.NUMBER
import io.github.lucasvallejoo.toylang.lexer.TokenType.OR
import io.github.lucasvallejoo.toylang.lexer.TokenType.PERCENT
import io.github.lucasvallejoo.toylang.lexer.TokenType.PLUS
import io.github.lucasvallejoo.toylang.lexer.TokenType.RBRACE
import io.github.lucasvallejoo.toylang.lexer.TokenType.RETURN
import io.github.lucasvallejoo.toylang.lexer.TokenType.RPAREN
import io.github.lucasvallejoo.toylang.lexer.TokenType.SLASH
import io.github.lucasvallejoo.toylang.lexer.TokenType.STAR
import io.github.lucasvallejoo.toylang.lexer.TokenType.STAR_STAR
import io.github.lucasvallejoo.toylang.lexer.TokenType.STRING
import io.github.lucasvallejoo.toylang.lexer.TokenType.THEN
import io.github.lucasvallejoo.toylang.lexer.TokenType.TRUE
import io.github.lucasvallejoo.toylang.lexer.TokenType.WHILE

/**
 * Turns a raw program source into a flat list of [Token]s.
 *
 * The lexer is a single-pass, non-regex scanner: it walks the input one
 * character at a time, classifying each character and emitting tokens as it
 * goes. The simplicity of a hand-written scanner makes the behaviour easy to
 * audit and is more than fast enough for programs of the sizes that appear
 * in this interpreter.
 *
 * ### What it recognises
 *
 *  - **Numbers.** Integers are lexed as [Long]; a decimal point followed by
 *    one or more digits turns them into a [Double]. Leading dots (`.5`) are
 *    rejected on purpose: every number must have at least one digit before
 *    the dot. This matches how Java and Go lex number literals.
 *  - **Strings.** Double-quoted (`"..."`) with the escape sequences `\n`,
 *    `\t`, `\"`, `\\`. Raw newlines inside a string are rejected so a
 *    forgotten closing quote produces a clean "unterminated string" error
 *    instead of swallowing half the program.
 *  - **Identifiers and keywords.** An identifier starts with a letter or
 *    underscore and continues with letters, digits or underscores. Once an
 *    identifier has been read, it is looked up in a keyword table: if it
 *    matches, its [TokenType] is swapped in before the token is emitted.
 *  - **Operators.** Multi-character operators (`==`, `!=`, `<=`, `>=`) are
 *    matched *greedily*: when the scanner sees a `<`, it peeks at the next
 *    character before deciding between [LT_EQ] and [LT].
 *  - **Line comments.** Two consecutive forward slashes (`//`) start a
 *    comment that runs to the next newline. The characters are discarded.
 *
 * ### Whitespace and statement separators
 *
 * Spaces, tabs, carriage returns and line feeds are all treated as pure
 * whitespace and never produce a token. Statement boundaries are therefore
 * **not** detected here: the parser uses commas and grammar to split
 * statements. This keeps the lexer simple and matches the sample programs,
 * which freely mix newlines and commas as separators.
 *
 * ### Errors
 *
 * Any character that does not fit one of the rules above raises a
 * [LexerException] with the exact line and column of the offending input.
 */
class Lexer(private val source: String) {

    private val tokens = mutableListOf<Token>()

    // Index into [source]: [start] is the first char of the token being built,
    // [current] is the next char to read. They coincide between tokens.
    private var start = 0
    private var current = 0

    // Current position as seen by the user (1-indexed).
    private var line = 1
    private var column = 1

    // Position where the token currently being built starts. Captured once
    // per token so that multi-char tokens still report their leading column.
    private var tokenStartLine = 1
    private var tokenStartColumn = 1

    /**
     * Every reserved word of the language, mapped to the [TokenType] the
     * lexer should emit when it sees that exact identifier. Identifiers that
     * do not appear here become [IDENTIFIER] tokens.
     */
    private val keywords: Map<String, TokenType> = mapOf(
        "if" to IF,
        "then" to THEN,
        "else" to ELSE,
        "while" to WHILE,
        "do" to DO,
        "fun" to FUN,
        "return" to RETURN,
        "true" to TRUE,
        "false" to FALSE,
        "and" to AND,
        "or" to OR,
        "not" to NOT,
    )

    /**
     * Scan the entire [source] and return the resulting list of tokens.
     *
     * The final token is always an [EOF] sentinel so that downstream stages
     * can rely on its presence without having to bound-check the list.
     */
    fun tokenize(): List<Token> {
        while (!isAtEnd()) {
            start = current
            tokenStartLine = line
            tokenStartColumn = column
            scanToken()
        }
        tokens.add(Token(EOF, "", null, line, column))
        return tokens
    }

    /**
     * Consume the next logical token from the input. Whitespace and comments
     * are silently discarded; they do not produce a token but still advance
     * the line/column counters so that error messages remain accurate.
     */
    private fun scanToken() {
        val c = advance()
        when (c) {
            // Single-character punctuation and operators
            '(' -> addToken(LPAREN)
            ')' -> addToken(RPAREN)
            '{' -> addToken(LBRACE)
            '}' -> addToken(RBRACE)
            ',' -> addToken(COMMA)
            '+' -> addToken(PLUS)
            '-' -> addToken(MINUS)
            '*' -> addToken(if (match('*')) STAR_STAR else STAR)
            '%' -> addToken(PERCENT)

            // One- or two-character operators: greedy match on the second char
            '=' -> addToken(if (match('=')) EQ_EQ else EQ)
            '<' -> addToken(if (match('=')) LT_EQ else LT)
            '>' -> addToken(if (match('=')) GT_EQ else GT)
            '!' -> if (match('=')) addToken(BANG_EQ) else error("Unexpected character '!'")

            // '/' is either the division operator or the start of a comment.
            // We resolve it by looking at the next character.
            '/' -> if (match('/')) skipLineComment() else addToken(SLASH)

            // String literal: read until the matching '"', honouring a small
            // set of escapes. The opening quote has already been consumed.
            '"' -> scanString()

            // Whitespace -- no token emitted. advance() already moved line/column.
            ' ', '\t', '\r', '\n' -> { /* ignore */ }

            else -> when {
                c.isDigit() -> scanNumber()
                isIdentifierStart(c) -> scanIdentifier()
                else -> error("Unexpected character '$c'")
            }
        }
    }

    /**
     * Read a numeric literal starting at [start]. The leading digit has
     * already been consumed by [scanToken]. Emits a [NUMBER] token whose
     * [Token.literal] is either a [Long] or a [Double] depending on whether
     * a fractional part was present.
     */
    private fun scanNumber() {
        while (peek().isDigit()) advance()

        // Fractional part: a '.' immediately followed by at least one digit.
        // The two-char lookahead protects us from consuming a '.' that has
        // no digit behind it (e.g. a future method-call syntax like `1.abs`).
        var isDouble = false
        if (peek() == '.' && peekNext().isDigit()) {
            isDouble = true
            advance() // consume '.'
            while (peek().isDigit()) advance()
        }

        val text = source.substring(start, current)
        val value: Any = if (isDouble) text.toDouble() else text.toLong()
        addToken(NUMBER, literal = value)
    }

    /**
     * Read a string literal starting just after the opening `"`. Builds the
     * decoded value (i.e., escapes are resolved into the characters they
     * stand for) and emits a [STRING] token whose [Token.literal] is the
     * decoded [String]. Raw newlines inside the literal are rejected: in
     * practice they always indicate a forgotten closing quote, and erroring
     * eagerly gives a much sharper diagnostic than swallowing arbitrary
     * source until the next `"` shows up many lines later.
     */
    private fun scanString() {
        val sb = StringBuilder()
        while (!isAtEnd() && peek() != '"') {
            val c = peek()
            if (c == '\n') error("Unterminated string literal")
            if (c == '\\') {
                // Capture the position of the backslash so the diagnostic for
                // an invalid escape points at the offending escape, not at
                // the start of the literal.
                val escLine = line
                val escColumn = column
                advance() // consume '\\'
                if (isAtEnd()) error("Unterminated string literal")
                val esc = advance()
                when (esc) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    else -> throw LexerException(
                        "Invalid escape sequence '\\$esc'",
                        escLine,
                        escColumn,
                    )
                }
            } else {
                advance()
                sb.append(c)
            }
        }
        if (isAtEnd()) error("Unterminated string literal")
        advance() // consume the closing '"'
        addToken(STRING, literal = sb.toString())
    }

    /**
     * Read an identifier or keyword starting at [start]. The leading letter
     * has already been consumed by [scanToken]. The final lexeme is looked
     * up in [keywords]; if found, the keyword type is emitted, otherwise
     * the token is an [IDENTIFIER] whose literal is the identifier name.
     */
    private fun scanIdentifier() {
        while (isIdentifierPart(peek())) advance()

        val text = source.substring(start, current)
        val type = keywords[text] ?: IDENTIFIER
        val literal: Any? = when (type) {
            IDENTIFIER -> text
            TRUE -> true
            FALSE -> false
            else -> null
        }
        addToken(type, literal = literal)
    }

    /**
     * Consume characters until the next newline, discarding them all. The
     * newline itself is left in the stream so that the outer loop can
     * update the line counter in the usual way.
     */
    private fun skipLineComment() {
        while (!isAtEnd() && peek() != '\n') advance()
    }

    // -------- Low-level helpers --------

    private fun isAtEnd(): Boolean = current >= source.length

    /**
     * Consume the next character, update line/column, and return it.
     */
    private fun advance(): Char {
        val c = source[current++]
        if (c == '\n') {
            line++
            column = 1
        } else {
            column++
        }
        return c
    }

    /**
     * Look at the next character without consuming it. Returns the null
     * character (`\u0000`) past end of input, which never matches any real
     * rule so the callers can compare safely without a dedicated sentinel.
     */
    private fun peek(): Char =
        if (isAtEnd()) '\u0000' else source[current]

    /**
     * Look one character further than [peek]. Same sentinel convention.
     */
    private fun peekNext(): Char =
        if (current + 1 >= source.length) '\u0000' else source[current + 1]

    /**
     * If the next character equals [expected], consume it and return `true`.
     * Otherwise leave the stream untouched and return `false`. Used to
     * decide between one-char and two-char operator tokens.
     */
    private fun match(expected: Char): Boolean {
        if (isAtEnd() || source[current] != expected) return false
        advance()
        return true
    }

    /**
     * Emit a token of the given [type] spanning the source slice
     * `[start, current)`. The token's position is the start of the slice,
     * not the current read position.
     */
    private fun addToken(type: TokenType, literal: Any? = null) {
        val lexeme = source.substring(start, current)
        tokens.add(Token(type, lexeme, literal, tokenStartLine, tokenStartColumn))
    }

    private fun isIdentifierStart(c: Char): Boolean = c.isLetter() || c == '_'
    private fun isIdentifierPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

    private fun error(message: String): Nothing =
        throw LexerException(message, tokenStartLine, tokenStartColumn)
}
