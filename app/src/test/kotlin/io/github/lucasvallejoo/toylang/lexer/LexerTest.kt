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
import io.github.lucasvallejoo.toylang.lexer.TokenType.PLUS
import io.github.lucasvallejoo.toylang.lexer.TokenType.RBRACE
import io.github.lucasvallejoo.toylang.lexer.TokenType.RETURN
import io.github.lucasvallejoo.toylang.lexer.TokenType.RPAREN
import io.github.lucasvallejoo.toylang.lexer.TokenType.STAR
import io.github.lucasvallejoo.toylang.lexer.TokenType.THEN
import io.github.lucasvallejoo.toylang.lexer.TokenType.TRUE
import io.github.lucasvallejoo.toylang.lexer.TokenType.WHILE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LexerTest {

    private fun tokenize(source: String): List<Token> = Lexer(source).tokenize()

    private fun types(source: String): List<TokenType> = tokenize(source).map { it.type }

    // -------------------- Basics --------------------

    @Test
    fun `empty input yields only EOF`() {
        assertEquals(listOf(EOF), types(""))
    }

    @Test
    fun `blank input yields only EOF`() {
        assertEquals(listOf(EOF), types("   \n\t\r\n "))
    }

    @Test
    fun `simple assignment is tokenised correctly`() {
        assertEquals(
            listOf(IDENTIFIER, EQ, NUMBER, EOF),
            types("x = 2"),
        )
    }

    // -------------------- Literals --------------------

    @Test
    fun `integer literals carry a Long literal`() {
        val tokens = tokenize("42")
        assertEquals(NUMBER, tokens[0].type)
        assertEquals(42L, tokens[0].literal)
    }

    @Test
    fun `double literals carry a Double literal`() {
        val tokens = tokenize("3.14")
        assertEquals(NUMBER, tokens[0].type)
        assertEquals(3.14, tokens[0].literal)
    }

    @Test
    fun `trailing dot without digit is not part of the number`() {
        // '1.' should lex as NUMBER(1) followed by a lone '.', which is
        // an illegal character at this stage. We lock the behaviour down
        // with a test because it is a common ambiguity in number lexers.
        assertThrows<LexerException> { tokenize("1.x") }
    }

    @Test
    fun `boolean literals produce TRUE and FALSE with boolean literals`() {
        val tokens = tokenize("true false")
        assertEquals(TRUE, tokens[0].type)
        assertEquals(true, tokens[0].literal)
        assertEquals(FALSE, tokens[1].type)
        assertEquals(false, tokens[1].literal)
    }

    // -------------------- Identifiers and keywords --------------------

    @Test
    fun `identifier literal holds the identifier name`() {
        val tokens = tokenize("foo_bar42")
        assertEquals(IDENTIFIER, tokens[0].type)
        assertEquals("foo_bar42", tokens[0].literal)
    }

    @Test
    fun `all reserved keywords are recognised`() {
        assertEquals(
            listOf(IF, THEN, ELSE, WHILE, DO, FUN, RETURN, TRUE, FALSE, AND, OR, NOT, EOF),
            types("if then else while do fun return true false and or not"),
        )
    }

    @Test
    fun `identifiers that merely start with a keyword are not keywords`() {
        // 'ifx' should be IDENTIFIER, not IF followed by IDENTIFIER.
        val tokens = tokenize("ifx while_")
        assertEquals(IDENTIFIER, tokens[0].type)
        assertEquals("ifx", tokens[0].literal)
        assertEquals(IDENTIFIER, tokens[1].type)
        assertEquals("while_", tokens[1].literal)
    }

    // -------------------- Operators --------------------

    @Test
    fun `two-character operators are matched greedily`() {
        assertEquals(
            listOf(EQ_EQ, BANG_EQ, LT_EQ, GT_EQ, LT, GT, EQ, EOF),
            types("== != <= >= < > ="),
        )
    }

    @Test
    fun `arithmetic operators are all distinct tokens`() {
        assertEquals(
            listOf(NUMBER, PLUS, NUMBER, MINUS, NUMBER, STAR, NUMBER, EOF),
            types("1 + 2 - 3 * 4"),
        )
    }

    @Test
    fun `lone bang is rejected because NOT is spelled as a keyword`() {
        // Logical negation uses the keyword 'not', so a bare '!' is invalid.
        assertThrows<LexerException> { tokenize("x = !true") }
    }

    // -------------------- Comments and whitespace --------------------

    @Test
    fun `line comments are skipped until end of line`() {
        assertEquals(
            listOf(IDENTIFIER, EQ, NUMBER, IDENTIFIER, EQ, NUMBER, EOF),
            types(
                """
                x = 1 // this is ignored
                y = 2
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `comment at end of file terminates cleanly`() {
        assertEquals(listOf(IDENTIFIER, EOF), types("x // trailing"))
    }

    @Test
    fun `positions are tracked across lines`() {
        val tokens = tokenize("x\n  y")
        assertEquals(1, tokens[0].line)
        assertEquals(1, tokens[0].column)
        assertEquals(2, tokens[1].line)
        assertEquals(3, tokens[1].column) // two leading spaces on line 2
    }

    // -------------------- Errors --------------------

    @Test
    fun `unexpected character raises LexerException with position`() {
        val e = assertThrows<LexerException> { tokenize("x = @") }
        assertEquals(1, e.line)
        assertEquals(5, e.column)
    }

    // -------------------- Full-sample smoke test --------------------

    @Test
    fun `recursive factorial sample from the brief tokenises cleanly`() {
        // fun fact_rec(n) { if n <= 0 then return 1 else return n*fact_rec(n-1) }
        val source = "fun fact_rec(n) { if n <= 0 then return 1 else return n*fact_rec(n-1) }"
        val expected = listOf(
            FUN, IDENTIFIER, LPAREN, IDENTIFIER, RPAREN, LBRACE,
            IF, IDENTIFIER, LT_EQ, NUMBER, THEN, RETURN, NUMBER,
            ELSE, RETURN, IDENTIFIER, STAR, IDENTIFIER, LPAREN,
            IDENTIFIER, MINUS, NUMBER, RPAREN,
            RBRACE, EOF,
        )
        assertEquals(expected, types(source))
    }

    @Test
    fun `while sample from the brief tokenises cleanly`() {
        // while x < 3 do if x == 1 then y = 10 else y = y + 1, x = x + 1
        val source = "while x < 3 do if x == 1 then y = 10 else y = y + 1, x = x + 1"
        val expected = listOf(
            WHILE, IDENTIFIER, LT, NUMBER, DO,
            IF, IDENTIFIER, EQ_EQ, NUMBER, THEN, IDENTIFIER, EQ, NUMBER,
            ELSE, IDENTIFIER, EQ, IDENTIFIER, PLUS, NUMBER,
            COMMA, IDENTIFIER, EQ, IDENTIFIER, PLUS, NUMBER,
            EOF,
        )
        assertEquals(expected, types(source))
    }
}
