package io.github.lucasvallejoo.toylang.parser

import io.github.lucasvallejoo.toylang.ast.Assignment
import io.github.lucasvallejoo.toylang.ast.Binary
import io.github.lucasvallejoo.toylang.ast.BinaryOp
import io.github.lucasvallejoo.toylang.ast.BoolLit
import io.github.lucasvallejoo.toylang.ast.Call
import io.github.lucasvallejoo.toylang.ast.Expr
import io.github.lucasvallejoo.toylang.ast.ExprStmt
import io.github.lucasvallejoo.toylang.ast.FunDecl
import io.github.lucasvallejoo.toylang.ast.If
import io.github.lucasvallejoo.toylang.ast.NumberLit
import io.github.lucasvallejoo.toylang.ast.Program
import io.github.lucasvallejoo.toylang.ast.Return
import io.github.lucasvallejoo.toylang.ast.Stmt
import io.github.lucasvallejoo.toylang.ast.StringLit
import io.github.lucasvallejoo.toylang.ast.Unary
import io.github.lucasvallejoo.toylang.ast.UnaryOp
import io.github.lucasvallejoo.toylang.ast.VarRef
import io.github.lucasvallejoo.toylang.ast.While
import io.github.lucasvallejoo.toylang.lexer.Token
import io.github.lucasvallejoo.toylang.lexer.TokenType
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
 * Turns a [Token] stream into a [Program] (AST).
 *
 * The parser is a hand-written recursive descent implementation with
 * *precedence climbing* for expressions: each level of precedence maps to
 * one method, and tighter-binding levels are called *from inside* looser
 * levels. This layout makes the precedence table directly visible in the
 * code, which is the whole point of writing a recursive descent parser by
 * hand.
 *
 * ### Precedence table (loosest to tightest)
 *
 * | Level | Operators              | Associativity |
 * |-------|------------------------|---------------|
 * | 1     | `or`                   | left          |
 * | 2     | `and`                  | left          |
 * | 3     | `not` (unary)          | right         |
 * | 4     | `==` `!=`              | left          |
 * | 5     | `<` `<=` `>` `>=`      | left          |
 * | 6     | `+` `-`                | left          |
 * | 7     | `*` `/` `%`            | left          |
 * | 8     | unary `-`              | right         |
 * | 9     | `**`                   | right         |
 * | 10    | function call          | n/a           |
 * | 11    | literals, identifiers, grouping | n/a  |
 *
 * `not` is *lower* than comparison on purpose: `not a == b` means
 * `not (a == b)`, matching Python and Kotlin. Unary arithmetic `-` stays
 * tight so that `-2 * 3` is `(-2) * 3`. `**` sits *tighter than* unary
 * `-` so that `-2 ** 2` reads as `-(2 ** 2) = -4`, again matching
 * Python; the operator is right-associative so `2 ** 3 ** 2` is
 * `2 ** (3 ** 2) = 512`.
 *
 * ### Statement-level rules
 *
 *  - The top level is a greedy sequence of statements with no explicit
 *    separator: each statement consumes as much as it can, and the next
 *    statement starts at whatever is left over. This works because
 *    expressions have bounded operator alphabets, so the parser always
 *    knows when an expression cannot be extended.
 *  - Inside a `while ... do` body or a `fun ... { }` body, statements are
 *    separated by commas. This mirrors the pattern used in the sample
 *    programs and keeps the trailing `x = x + 1` of sample 3 inside the
 *    while loop.
 *  - The two branches of `if then else` are each a *single* simple
 *    statement. The `else` is **mandatory** (there is no dangling-else
 *    problem to solve because there is no optional else to dangle).
 *  - Function declarations are only legal at the top level. Nested
 *    functions are rejected because `fun` is not a valid start for a
 *    simple statement.
 *  - Assignment is a *statement*, not an expression: `a = b = 0` does not
 *    parse. This keeps expressions side-effect free and matches every
 *    sample in the brief.
 *
 * ### Errors
 *
 * Grammar violations raise a [ParserException] carrying the exact line and
 * column of the offending token and a message of the shape
 * `"Expected X, found Y"`. No attempt is made to recover and keep parsing
 * -- in an MVP interpreter, the first error is the one that matters.
 */
class Parser(private val tokens: List<Token>) {

    private var current = 0

    /**
     * Parse the full token stream and return the resulting [Program].
     * Fails with [ParserException] if the input is not a valid program.
     */
    fun parse(): Program {
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            statements.add(parseTopLevelStmt())
        }
        return Program(statements)
    }

    // -------- Statements --------

    private fun parseTopLevelStmt(): Stmt =
        if (check(FUN)) parseFunDecl() else parseSimpleStmt()

    /**
     * A *simple* statement is anything that can legally appear inside a
     * body_seq: assignments, control flow, return, and bare expressions.
     * `fun` is explicitly NOT simple -- that is how we reject nested
     * function declarations.
     */
    private fun parseSimpleStmt(): Stmt {
        val t = peek()
        return when (t.type) {
            IF -> parseIf()
            WHILE -> parseWhile()
            RETURN -> parseReturn()
            IDENTIFIER -> parseAssignmentOrExprStmt()
            NUMBER, STRING, TRUE, FALSE, LPAREN, NOT, MINUS -> parseExprStmt()
            else -> throw ParserException(
                "Expected statement, found ${t.type}",
                t.line,
                t.column,
            )
        }
    }

    private fun parseFunDecl(): FunDecl {
        val funToken = expect(FUN, "Expected 'fun'")
        val nameToken = expect(IDENTIFIER, "Expected function name after 'fun'")
        val name = nameToken.literal as String

        expect(LPAREN, "Expected '(' after function name")
        val params = parseParameterList()
        expect(RPAREN, "Expected ')' after parameter list")

        expect(LBRACE, "Expected '{' to start function body")
        // An empty function body (`fun g() { }`) is a no-op but still legal
        // grammar; we special-case it so parseBodySeq can insist on at
        // least one statement everywhere else.
        val body = if (check(RBRACE)) emptyList() else parseBodySeq()
        expect(RBRACE, "Expected '}' to close function body")

        return FunDecl(name, params, body, funToken.line, funToken.column)
    }

    private fun parseParameterList(): List<String> {
        if (check(RPAREN)) return emptyList()
        val params = mutableListOf<String>()
        params.add(expect(IDENTIFIER, "Expected parameter name").literal as String)
        while (match(COMMA)) {
            params.add(expect(IDENTIFIER, "Expected parameter name after ','").literal as String)
        }
        return params
    }

    /**
     * A body_seq is a comma-separated list of simple statements with at
     * least one entry. Used for `while` bodies directly and for `fun`
     * bodies via the empty-`{}` short-circuit in [parseFunDecl]. Requiring
     * at least one statement means that `while true do fun g() { }` errors
     * instead of sneaking the `fun` back out to the top level via an empty
     * loop body.
     */
    private fun parseBodySeq(): List<Stmt> {
        val stmts = mutableListOf<Stmt>()
        stmts.add(parseSimpleStmt())
        while (match(COMMA)) {
            stmts.add(parseSimpleStmt())
        }
        return stmts
    }

    private fun parseIf(): If {
        val ifToken = expect(IF, "Expected 'if'")
        val condition = parseExpression()
        expect(THEN, "Expected 'then' after if-condition")
        val thenBranch = parseSimpleStmt()
        expect(ELSE, "Expected 'else' after if-then branch")
        val elseBranch = parseSimpleStmt()
        return If(condition, thenBranch, elseBranch, ifToken.line, ifToken.column)
    }

    private fun parseWhile(): While {
        val whileToken = expect(WHILE, "Expected 'while'")
        val condition = parseExpression()
        expect(DO, "Expected 'do' after while-condition")
        val body = parseBodySeq()
        return While(condition, body, whileToken.line, whileToken.column)
    }

    private fun parseReturn(): Return {
        val retToken = expect(RETURN, "Expected 'return'")
        val value = parseExpression()
        return Return(value, retToken.line, retToken.column)
    }

    /**
     * Disambiguate between an assignment and a bare expression-statement.
     * Both start with an `IDENTIFIER`; the deciding factor is whether the
     * *next* token is `=` (assignment) or `(` / an operator / nothing
     * (expression).
     */
    private fun parseAssignmentOrExprStmt(): Stmt {
        val idToken = peek()
        if (peekNext().type == EQ) {
            advance() // IDENTIFIER
            advance() // '='
            val value = parseExpression()
            return Assignment(idToken.literal as String, value, idToken.line, idToken.column)
        }
        return parseExprStmt()
    }

    private fun parseExprStmt(): ExprStmt {
        val expr = parseExpression()
        return ExprStmt(expr, expr.line, expr.column)
    }

    // -------- Expressions (precedence climbing) --------

    private fun parseExpression(): Expr = parseOr()

    private fun parseOr(): Expr = parseLeftAssocBinary(
        next = ::parseAnd,
        ops = mapOf(OR to BinaryOp.OR),
    )

    private fun parseAnd(): Expr = parseLeftAssocBinary(
        next = ::parseLogicalNot,
        ops = mapOf(AND to BinaryOp.AND),
    )

    /**
     * `not` sits *below* comparison in the precedence table so that
     * `not a == b` reads as `not (a == b)`. It is right-associative: we
     * recurse on ourselves so that `not not x` produces two nested unary
     * nodes.
     */
    private fun parseLogicalNot(): Expr {
        if (check(NOT)) {
            val op = advance()
            val operand = parseLogicalNot()
            return Unary(UnaryOp.NOT, operand, op.line, op.column)
        }
        return parseEquality()
    }

    private fun parseEquality(): Expr = parseLeftAssocBinary(
        next = ::parseComparison,
        ops = mapOf(EQ_EQ to BinaryOp.EQ, BANG_EQ to BinaryOp.NEQ),
    )

    private fun parseComparison(): Expr = parseLeftAssocBinary(
        next = ::parseTerm,
        ops = mapOf(
            LT to BinaryOp.LT,
            LT_EQ to BinaryOp.LE,
            GT to BinaryOp.GT,
            GT_EQ to BinaryOp.GE,
        ),
    )

    private fun parseTerm(): Expr = parseLeftAssocBinary(
        next = ::parseFactor,
        ops = mapOf(PLUS to BinaryOp.PLUS, MINUS to BinaryOp.MINUS),
    )

    private fun parseFactor(): Expr = parseLeftAssocBinary(
        next = ::parseUnary,
        ops = mapOf(
            STAR to BinaryOp.TIMES,
            SLASH to BinaryOp.DIV,
            PERCENT to BinaryOp.MOD,
        ),
    )

    /**
     * Unary arithmetic negation only. Right-associative via self-recursion
     * so that `- - x` produces two nested unary nodes. Falls through to
     * [parsePower], which is *tighter* than unary -- `-2 ** 2` therefore
     * reads as `-(2 ** 2)`, the Python convention.
     */
    private fun parseUnary(): Expr {
        if (check(MINUS)) {
            val op = advance()
            val operand = parseUnary()
            return Unary(UnaryOp.NEG, operand, op.line, op.column)
        }
        return parsePower()
    }

    /**
     * Power (`**`). Right-associative, tighter than unary `-` on the
     * **left** but loose enough on the **right** that `2 ** -3` parses
     * as `2 ** (-3)`. This asymmetric rule is the Python convention: the
     * left operand of `**` is taken from a tight context (no leading
     * unary `-`, that would belong to an enclosing [parseUnary] frame),
     * while the right operand re-enters [parseUnary] so leading sign
     * flips are absorbed into the right side.
     */
    private fun parsePower(): Expr {
        val left = parseCall()
        if (check(STAR_STAR)) {
            val opToken = advance()
            val right = parseUnary()
            return Binary(BinaryOp.POW, left, right, opToken.line, opToken.column)
        }
        return left
    }

    /**
     * A function call is a plain `IDENTIFIER` followed *immediately* by
     * `(`. Anything else falls through to [parsePrimary], which turns the
     * identifier into a [VarRef].
     */
    private fun parseCall(): Expr {
        if (check(IDENTIFIER) && peekNext().type == LPAREN) {
            val idToken = advance()
            expect(LPAREN, "Expected '('")
            val args = parseArgumentList()
            expect(RPAREN, "Expected ')' after arguments")
            return Call(idToken.literal as String, args, idToken.line, idToken.column)
        }
        return parsePrimary()
    }

    private fun parseArgumentList(): List<Expr> {
        if (check(RPAREN)) return emptyList()
        val args = mutableListOf<Expr>()
        args.add(parseExpression())
        while (match(COMMA)) {
            args.add(parseExpression())
        }
        return args
    }

    private fun parsePrimary(): Expr {
        val token = peek()
        return when (token.type) {
            NUMBER -> {
                advance()
                NumberLit(token.literal!!, token.line, token.column)
            }
            STRING -> {
                advance()
                StringLit(token.literal as String, token.line, token.column)
            }
            TRUE -> {
                advance()
                BoolLit(true, token.line, token.column)
            }
            FALSE -> {
                advance()
                BoolLit(false, token.line, token.column)
            }
            IDENTIFIER -> {
                advance()
                VarRef(token.literal as String, token.line, token.column)
            }
            LPAREN -> {
                advance()
                val expr = parseExpression()
                expect(RPAREN, "Expected ')' to close grouping")
                expr
            }
            else -> throw ParserException(
                "Expected expression, found ${token.type}",
                token.line,
                token.column,
            )
        }
    }

    /**
     * Shared skeleton for every left-associative binary level of the
     * precedence table. [next] parses the tighter-binding level, [ops]
     * maps each matchable [TokenType] to the [BinaryOp] it produces.
     */
    private fun parseLeftAssocBinary(
        next: () -> Expr,
        ops: Map<TokenType, BinaryOp>,
    ): Expr {
        var left = next()
        while (!isAtEnd() && peek().type in ops) {
            val opToken = advance()
            val op = ops.getValue(opToken.type)
            val right = next()
            left = Binary(op, left, right, opToken.line, opToken.column)
        }
        return left
    }

    // -------- Low-level helpers --------

    private fun peek(): Token = tokens[current]

    private fun peekNext(): Token =
        if (current + 1 < tokens.size) tokens[current + 1] else tokens.last()

    private fun isAtEnd(): Boolean = peek().type == EOF

    private fun check(type: TokenType): Boolean =
        !isAtEnd() && peek().type == type

    private fun advance(): Token {
        val t = tokens[current]
        if (!isAtEnd()) current++
        return t
    }

    private fun match(type: TokenType): Boolean {
        if (!check(type)) return false
        advance()
        return true
    }

    private fun expect(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        val t = peek()
        throw ParserException("$message, found ${t.type}", t.line, t.column)
    }

}
