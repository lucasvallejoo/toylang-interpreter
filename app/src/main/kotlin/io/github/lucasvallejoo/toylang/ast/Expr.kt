package io.github.lucasvallejoo.toylang.ast

/**
 * Every binary operator the parser can emit. The [symbol] property carries
 * the source-level spelling and is used by the pretty-printer and by error
 * messages so that diagnostics look like what the user actually wrote.
 */
enum class BinaryOp(val symbol: String) {
    OR("or"),
    AND("and"),
    EQ("=="),
    NEQ("!="),
    LT("<"),
    LE("<="),
    GT(">"),
    GE(">="),
    PLUS("+"),
    MINUS("-"),
    TIMES("*"),
    DIV("/"),
    MOD("%"),
}

/**
 * Unary operators. Logical `not` and arithmetic `-` (negation) are the only
 * ones the language supports; a dedicated enum keeps them typed apart from
 * their binary siblings.
 */
enum class UnaryOp(val symbol: String) {
    NOT("not"),
    NEG("-"),
}

/**
 * An expression node. Every [Expr] carries the [line] and [column] of the
 * token that produced it so that the interpreter can point at the exact spot
 * when a runtime error occurs (division by zero, undefined variable, etc.).
 *
 * The hierarchy is sealed: exhaustive `when` branches over [Expr] are
 * checked at compile time, which is a sharp safety net while the AST grows.
 */
sealed class Expr {
    abstract val line: Int
    abstract val column: Int
}

/**
 * A numeric literal. [value] is either [Long] (integer literal, e.g. `42`)
 * or [Double] (fractional literal, e.g. `3.14`). The distinction is settled
 * once, in the lexer, and never re-parsed here.
 */
data class NumberLit(
    val value: Any,
    override val line: Int,
    override val column: Int,
) : Expr()

/** A boolean literal produced by the keywords `true` or `false`. */
data class BoolLit(
    val value: Boolean,
    override val line: Int,
    override val column: Int,
) : Expr()

/** A reference to a variable by name. Resolution happens at runtime. */
data class VarRef(
    val name: String,
    override val line: Int,
    override val column: Int,
) : Expr()

/**
 * A binary operation (`left op right`). The operator is always known at
 * parse time; operands are recursively other expressions.
 */
data class Binary(
    val op: BinaryOp,
    val left: Expr,
    val right: Expr,
    override val line: Int,
    override val column: Int,
) : Expr()

/** A unary operation (`op operand`), either `not x` or `-x`. */
data class Unary(
    val op: UnaryOp,
    val operand: Expr,
    override val line: Int,
    override val column: Int,
) : Expr()

/**
 * A function call. Only direct calls by name are supported -- expressions
 * like `(f)(x)` never appear as [Call] because the grammar forbids them.
 */
data class Call(
    val callee: String,
    val args: List<Expr>,
    override val line: Int,
    override val column: Int,
) : Expr()
