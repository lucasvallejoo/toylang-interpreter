package io.github.lucasvallejoo.toylang.ast

/**
 * S-expression pretty-printer for the AST.
 *
 * The format is Lisp-like because s-expressions make operator nesting
 * obvious at a glance -- which is exactly what we want from a debug dump
 * of the parse tree. Numbers carry their type suffix (`2` vs `2.0`) so the
 * Long/Double split is visible.
 *
 * Example:
 * ```
 * (= x 2)
 * (= y (* (+ x 2) 2))
 * ```
 *
 * Complex statements are broken across multiple lines, indented two spaces
 * per level:
 * ```
 * (while (< x 3)
 *   (if (== x 1)
 *     (= y 10)
 *     (= y (+ y 1)))
 *   (= x (+ x 1)))
 * ```
 */

fun Program.sexpr(): String =
    statements.joinToString("\n") { it.sexpr(0) }

fun Stmt.sexpr(indent: Int = 0): String {
    val pad = "  ".repeat(indent)
    return when (this) {
        is Assignment -> "$pad(= $name ${value.sexpr()})"
        is ExprStmt -> "$pad${expr.sexpr()}"
        is Return -> "$pad(return ${value.sexpr()})"
        is If -> buildString {
            append(pad)
            append("(if ").append(condition.sexpr())
            append('\n').append(thenBranch.sexpr(indent + 1))
            append('\n').append(elseBranch.sexpr(indent + 1))
            append(')')
        }
        is While -> buildString {
            append(pad)
            append("(while ").append(condition.sexpr())
            for (stmt in body) append('\n').append(stmt.sexpr(indent + 1))
            append(')')
        }
        is FunDecl -> buildString {
            append(pad)
            append("(fun ").append(name)
            append(" (").append(params.joinToString(" ")).append(')')
            for (stmt in body) append('\n').append(stmt.sexpr(indent + 1))
            append(')')
        }
    }
}

fun Expr.sexpr(): String = when (this) {
    is NumberLit -> value.toString()
    is BoolLit -> value.toString()
    is StringLit -> value.toToylangLiteral()
    is VarRef -> name
    is Binary -> "(${op.symbol} ${left.sexpr()} ${right.sexpr()})"
    is Unary -> "(${op.symbol} ${operand.sexpr()})"
    is Call -> if (args.isEmpty()) "($callee)"
              else "($callee ${args.joinToString(" ") { it.sexpr() }})"
}

/**
 * Render a Kotlin [String] back into its Toylang source-literal form,
 * with surrounding double quotes and the same set of escape sequences
 * the lexer accepts. Used both by the AST pretty-printer and by the
 * runtime's [io.github.lucasvallejoo.toylang.runtime.StringVal] when
 * top-level variables are dumped at the end of execution, so the user
 * sees a literal that they could paste back into a program.
 */
internal fun String.toToylangLiteral(): String {
    val sb = StringBuilder("\"")
    for (c in this) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
    }
    sb.append('"')
    return sb.toString()
}
