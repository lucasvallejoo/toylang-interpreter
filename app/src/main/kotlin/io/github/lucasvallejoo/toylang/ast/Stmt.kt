package io.github.lucasvallejoo.toylang.ast

/**
 * A statement node. Like [Expr], every statement remembers its source
 * position so that the evaluator can blame the right line when something
 * goes wrong at runtime.
 *
 * Statements come in two flavours in this language:
 *  - *simple* statements ([Assignment], [If], [While], [Return], [ExprStmt])
 *    can appear anywhere a statement is legal;
 *  - [FunDecl] is a *top-level-only* statement. The parser rejects it
 *    anywhere else, so after parsing we never see a nested fun declaration.
 */
sealed class Stmt {
    abstract val line: Int
    abstract val column: Int
}

/**
 * A variable assignment, `name = value`. The same node is used for the
 * first introduction of a variable and for subsequent reassignments -- the
 * language has no declaration keyword.
 */
data class Assignment(
    val name: String,
    val value: Expr,
    override val line: Int,
    override val column: Int,
) : Stmt()

/**
 * An `if ... then ... else ...` statement. Both branches are a single
 * simple statement: this matches the pattern in the sample programs and
 * keeps the grammar unambiguous (no dangling-else problem).
 */
data class If(
    val condition: Expr,
    val thenBranch: Stmt,
    val elseBranch: Stmt,
    override val line: Int,
    override val column: Int,
) : Stmt()

/**
 * A `while cond do body` loop. [body] is a list of simple statements
 * separated by commas in the source, mirroring the pattern in sample 3
 * of the brief.
 */
data class While(
    val condition: Expr,
    val body: List<Stmt>,
    override val line: Int,
    override val column: Int,
) : Stmt()

/**
 * A `return expr` statement. Only legal inside a function body; the parser
 * accepts it anywhere and the evaluator (or a future semantic pass) will
 * complain if it escapes.
 */
data class Return(
    val value: Expr,
    override val line: Int,
    override val column: Int,
) : Stmt()

/**
 * A function declaration. [body] is a list of simple statements drawn from
 * between the `{` and `}`; they are separated by commas, same rule as the
 * body of a `while` loop.
 */
data class FunDecl(
    val name: String,
    val params: List<String>,
    val body: List<Stmt>,
    override val line: Int,
    override val column: Int,
) : Stmt()

/**
 * A statement that is "just an expression". In practice this is a function
 * call invoked for its side effects on globals; more exotic uses (`3 + 4;`)
 * are grammatically valid but pointless in a language without I/O.
 */
data class ExprStmt(
    val expr: Expr,
    override val line: Int,
    override val column: Int,
) : Stmt()
