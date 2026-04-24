package io.github.lucasvallejoo.toylang.runtime

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
import io.github.lucasvallejoo.toylang.ast.Unary
import io.github.lucasvallejoo.toylang.ast.UnaryOp
import io.github.lucasvallejoo.toylang.ast.VarRef
import io.github.lucasvallejoo.toylang.ast.While

/**
 * A tree-walking interpreter for Toylang.
 *
 * The evaluator owns an [Environment] and applies each AST node to it.
 * Expressions produce a [Value]; statements produce side effects on the
 * environment (or throw). A program is executed in two passes so that
 * functions can refer to each other regardless of textual order:
 *
 *  1. Every top-level `fun` declaration is registered.
 *  2. The remaining top-level statements are executed in order.
 *
 * When evaluation can no longer continue -- an undefined name, a type
 * mismatch, a division by zero -- the evaluator raises an
 * [EvaluationException] carrying the source position of the offending
 * node, so the CLI can print a diagnostic that points at the user's code.
 */
class Evaluator {

    val environment: Environment = Environment()

    /**
     * Run [program] to completion and return the environment that holds
     * its final state. The caller is responsible for printing the
     * top-level variables; see [Environment.topLevelVariables].
     */
    fun run(program: Program): Environment {
        for (stmt in program.statements) {
            if (stmt is FunDecl) environment.defineFunction(stmt)
        }
        for (stmt in program.statements) {
            if (stmt !is FunDecl) exec(stmt)
        }
        return environment
    }

    // -------- Statements --------

    private fun exec(stmt: Stmt) {
        when (stmt) {
            is Assignment -> environment.setVariable(stmt.name, eval(stmt.value))
            is ExprStmt -> eval(stmt.expr)
            is If -> execIf(stmt)
            is While -> execWhile(stmt)
            is Return -> throw ReturnSignal(eval(stmt.value))
            is FunDecl -> environment.defineFunction(stmt)
        }
    }

    private fun execIf(stmt: If) {
        val cond = asBool(eval(stmt.condition), stmt.condition)
        if (cond) exec(stmt.thenBranch) else exec(stmt.elseBranch)
    }

    private fun execWhile(stmt: While) {
        while (asBool(eval(stmt.condition), stmt.condition)) {
            for (s in stmt.body) exec(s)
        }
    }

    // -------- Expressions --------

    private fun eval(expr: Expr): Value = when (expr) {
        is NumberLit -> when (val v = expr.value) {
            is Long -> LongVal(v)
            is Double -> DoubleVal(v)
            else -> throw EvaluationException(
                "internal error: number literal holds unexpected type ${v::class.simpleName}",
                expr.line, expr.column,
            )
        }
        is BoolLit -> BoolVal(expr.value)
        is VarRef -> environment.getVariable(expr.name)
            ?: throw EvaluationException("undefined variable '${expr.name}'", expr.line, expr.column)
        is Unary -> evalUnary(expr)
        is Binary -> evalBinary(expr)
        is Call -> evalCall(expr)
    }

    private fun evalUnary(expr: Unary): Value {
        val operand = eval(expr.operand)
        return when (expr.op) {
            UnaryOp.NOT -> BoolVal(!asBool(operand, expr.operand))
            UnaryOp.NEG -> when (operand) {
                is LongVal -> LongVal(-operand.value)
                is DoubleVal -> DoubleVal(-operand.value)
                else -> throw typeError(
                    "unary '-' expects a number, got ${typeName(operand)}",
                    expr,
                )
            }
        }
    }

    private fun evalBinary(expr: Binary): Value {
        // Short-circuit: we must not evaluate the right operand until we
        // know whether the left one already decides the outcome.
        if (expr.op == BinaryOp.AND || expr.op == BinaryOp.OR) {
            val left = asBool(eval(expr.left), expr.left)
            val shortCircuit = (expr.op == BinaryOp.OR && left) || (expr.op == BinaryOp.AND && !left)
            if (shortCircuit) return BoolVal(left)
            val right = asBool(eval(expr.right), expr.right)
            return BoolVal(right)
        }

        val left = eval(expr.left)
        val right = eval(expr.right)

        return when (expr.op) {
            BinaryOp.EQ -> BoolVal(valuesEqual(left, right))
            BinaryOp.NEQ -> BoolVal(!valuesEqual(left, right))
            BinaryOp.LT, BinaryOp.LE, BinaryOp.GT, BinaryOp.GE -> evalComparison(expr.op, left, right, expr)
            BinaryOp.PLUS, BinaryOp.MINUS, BinaryOp.TIMES,
            BinaryOp.DIV, BinaryOp.MOD -> evalArithmetic(expr.op, left, right, expr)
            BinaryOp.AND, BinaryOp.OR -> error("unreachable: short-circuit handled above")
        }
    }

    private fun evalArithmetic(op: BinaryOp, left: Value, right: Value, expr: Binary): Value {
        requireNumeric(left, expr.left)
        requireNumeric(right, expr.right)

        // If either side is Double, promote to Double and compute in f64.
        // Otherwise both are Long and we stay in integer arithmetic --
        // this is what keeps `7 / 2` yielding `3`, not `3.5`.
        val promote = left is DoubleVal || right is DoubleVal
        if (promote) {
            val a = toDouble(left)
            val b = toDouble(right)
            return when (op) {
                BinaryOp.PLUS -> DoubleVal(a + b)
                BinaryOp.MINUS -> DoubleVal(a - b)
                BinaryOp.TIMES -> DoubleVal(a * b)
                BinaryOp.DIV -> {
                    if (b == 0.0) throw divByZero(expr)
                    DoubleVal(a / b)
                }
                BinaryOp.MOD -> {
                    if (b == 0.0) throw divByZero(expr)
                    DoubleVal(a % b)
                }
                else -> error("unreachable")
            }
        }

        val a = (left as LongVal).value
        val b = (right as LongVal).value
        return when (op) {
            BinaryOp.PLUS -> LongVal(a + b)
            BinaryOp.MINUS -> LongVal(a - b)
            BinaryOp.TIMES -> LongVal(a * b)
            BinaryOp.DIV -> {
                if (b == 0L) throw divByZero(expr)
                LongVal(a / b)
            }
            BinaryOp.MOD -> {
                if (b == 0L) throw divByZero(expr)
                LongVal(a % b)
            }
            else -> error("unreachable")
        }
    }

    private fun evalComparison(op: BinaryOp, left: Value, right: Value, expr: Binary): Value {
        requireNumeric(left, expr.left)
        requireNumeric(right, expr.right)
        val a = toDouble(left)
        val b = toDouble(right)
        val result = when (op) {
            BinaryOp.LT -> a < b
            BinaryOp.LE -> a <= b
            BinaryOp.GT -> a > b
            BinaryOp.GE -> a >= b
            else -> error("unreachable")
        }
        return BoolVal(result)
    }

    private fun valuesEqual(left: Value, right: Value): Boolean = when {
        left is LongVal && right is LongVal -> left.value == right.value
        left is DoubleVal && right is DoubleVal -> left.value == right.value
        // Cross-number equality compares numerically, not by type.
        left is LongVal && right is DoubleVal -> left.value.toDouble() == right.value
        left is DoubleVal && right is LongVal -> left.value == right.value.toDouble()
        left is BoolVal && right is BoolVal -> left.value == right.value
        // Mixing booleans and numbers (or anything involving a function) is
        // never equal -- not an error: `1 == true` just yields `false`.
        else -> false
    }

    private fun evalCall(expr: Call): Value {
        val fn = environment.getFunction(expr.callee)
            ?: throw EvaluationException(
                "undefined function '${expr.callee}'",
                expr.line, expr.column,
            )
        val decl = fn.decl
        if (expr.args.size != decl.params.size) {
            throw EvaluationException(
                "function '${decl.name}' expects ${decl.params.size} argument(s), got ${expr.args.size}",
                expr.line, expr.column,
            )
        }

        // Evaluate arguments in the caller's scope before pushing the frame,
        // otherwise the parameters would shadow same-named outer variables
        // mid-evaluation.
        val evaluated = expr.args.map { eval(it) }
        val frame: MutableMap<String, Value> = hashMapOf()
        for ((i, param) in decl.params.withIndex()) {
            frame[param] = evaluated[i]
        }

        return try {
            environment.inFrame(frame) {
                for (s in decl.body) exec(s)
                // Falling off the end without `return` is an error: the
                // language has no `unit` / `void`, every call must produce
                // a value.
                throw EvaluationException(
                    "function '${decl.name}' finished without returning a value",
                    expr.line, expr.column,
                )
            }
        } catch (signal: ReturnSignal) {
            signal.value
        }
    }

    // -------- Helpers --------

    private fun asBool(value: Value, node: Expr): Boolean = when (value) {
        is BoolVal -> value.value
        else -> throw typeError("expected a boolean, got ${typeName(value)}", node)
    }

    private fun requireNumeric(value: Value, node: Expr) {
        if (value !is LongVal && value !is DoubleVal) {
            throw typeError("expected a number, got ${typeName(value)}", node)
        }
    }

    private fun toDouble(value: Value): Double = when (value) {
        is LongVal -> value.value.toDouble()
        is DoubleVal -> value.value
        else -> error("requireNumeric should have rejected this")
    }

    private fun typeName(value: Value): String = when (value) {
        is LongVal -> "int"
        is DoubleVal -> "float"
        is BoolVal -> "bool"
        is FunVal -> "function"
    }

    private fun typeError(message: String, node: Expr): EvaluationException =
        EvaluationException(message, node.line, node.column)

    private fun divByZero(expr: Binary): EvaluationException =
        EvaluationException("division by zero", expr.line, expr.column)
}

/**
 * Control-flow signal used to unwind a `return` statement out of any
 * depth of nested blocks back to the nearest [Evaluator.evalCall].
 *
 * It is *not* an error: it inherits from [RuntimeException] only because
 * throw/catch is the simplest way to bail out of a recursive walk in a
 * tree-walking interpreter. The stack trace is suppressed because
 * filling it on every `return` would be pure waste.
 */
private class ReturnSignal(val value: Value) : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}