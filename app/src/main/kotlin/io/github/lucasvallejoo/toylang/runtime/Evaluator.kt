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
import io.github.lucasvallejoo.toylang.ast.StringLit
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
 * Calls are resolved **user-first, built-ins second**: a `fun print(x)`
 * declaration in the program transparently shadows the language's own
 * `print` because the lookup tries the environment before falling back
 * to the built-in registry. This keeps shadowing predictable -- if the
 * name is yours in the source, it is yours at runtime too.
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
        is StringLit -> StringVal(expr.value)
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
            // `+` is overloaded: numeric addition for numbers, concatenation
            // when both sides are strings. Mixing string and number falls
            // through to evalArithmetic, which produces a clear "expected a
            // number, got string" error -- intentional, no implicit coercion.
            BinaryOp.PLUS -> if (left is StringVal && right is StringVal) {
                StringVal(left.value + right.value)
            } else {
                evalArithmetic(expr.op, left, right, expr)
            }
            BinaryOp.MINUS, BinaryOp.TIMES,
            BinaryOp.DIV, BinaryOp.MOD -> evalArithmetic(expr.op, left, right, expr)
            BinaryOp.POW -> evalPower(left, right, expr)
            BinaryOp.AND, BinaryOp.OR -> error("unreachable: short-circuit handled above")
        }
    }

    /**
     * Exponentiation. The result type follows the same rule as the rest
     * of the arithmetic, with one extra wrinkle: a *negative* exponent
     * always promotes to [Double], even if both operands are [Long]. This
     * is the only way to express `2 ** -1 = 0.5` honestly -- promoting
     * eagerly is preferable to silently truncating to `0`.
     *
     * Integer exponentiation is computed in a fast-doubling loop instead
     * of via `Math.pow`, so small integer powers stay fully precise even
     * for large bases (where `Math.pow`'s `Double` would already lose
     * precision in the upper Long range).
     */
    private fun evalPower(left: Value, right: Value, expr: Binary): Value {
        requireNumeric(left, expr.left)
        requireNumeric(right, expr.right)

        if (left is LongVal && right is LongVal && right.value >= 0) {
            return LongVal(intPow(left.value, right.value))
        }
        val a = toDouble(left)
        val b = toDouble(right)
        return DoubleVal(Math.pow(a, b))
    }

    /**
     * Repeated-squaring integer power: O(log exp) multiplications. The
     * caller guarantees `exp >= 0`. Overflow wraps (Kotlin's default for
     * `Long *`); flagging it would require an explicit overflow check on
     * every multiplication and is intentionally left out of the MVP.
     */
    private fun intPow(base: Long, exp: Long): Long {
        var result = 1L
        var b = base
        var e = exp
        while (e > 0) {
            if (e and 1L == 1L) result *= b
            b *= b
            e = e shr 1
        }
        return result
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
        left is StringVal && right is StringVal -> left.value == right.value
        // Mixing categories (string vs number, bool vs anything else, etc.)
        // is never equal -- not an error: `1 == true` and `"1" == 1` both
        // yield `false`. This keeps `==` total over every pair of values.
        else -> false
    }

    private fun evalCall(expr: Call): Value {
        // User-defined first; built-ins are a fallback for unknown names.
        val fn = environment.getFunction(expr.callee)
        if (fn != null) return callUserFunction(expr, fn.decl)

        invokeBuiltin(expr)?.let { return it }

        throw EvaluationException(
            "undefined function '${expr.callee}'",
            expr.line, expr.column,
        )
    }

    private fun callUserFunction(expr: Call, decl: FunDecl): Value {
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

    /**
     * Dispatch table for built-in functions. Returns the result of the
     * call, or `null` if [expr] does not target a known built-in -- the
     * caller treats that as "unknown function".
     *
     * The list is intentionally tiny: anything more elaborate (`len`,
     * `str`, etc.) belongs in a follow-up. Each built-in is responsible
     * for evaluating its own arguments because the rules vary -- `print`
     * accepts any arity, while a hypothetical `len` would insist on one.
     */
    private fun invokeBuiltin(expr: Call): Value? = when (expr.callee) {
        "print" -> invokePrint(expr)
        else -> null
    }

    /**
     * The `print` built-in. Accepts any number of arguments, evaluates
     * them in the caller's scope, and writes them to standard output
     * separated by spaces and followed by a newline.
     *
     * Strings are emitted via [Value.asText] (no surrounding quotes), so
     * `print("hello")` produces `hello`. Numbers and booleans use their
     * normal display form. The return value is `LongVal(0)` so the call
     * is also legal in expression position; in practice callers use it
     * as a bare statement and discard the result.
     */
    private fun invokePrint(expr: Call): Value {
        val rendered = expr.args.joinToString(" ") { eval(it).asText() }
        println(rendered)
        return LongVal(0)
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
        is StringVal -> "string"
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