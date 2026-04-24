package io.github.lucasvallejoo.toylang.runtime

import io.github.lucasvallejoo.toylang.ast.FunDecl

/**
 * A runtime value produced by evaluating an expression.
 *
 * The sealed hierarchy mirrors the literal types supported by the language
 * plus [FunVal], which is what the environment stores when it sees a
 * `fun` declaration. Every value is immutable; operations on values
 * always produce new values rather than mutating the operands.
 */
sealed class Value {
    /**
     * Render the value the way the interpreter should print it on
     * standard output. [LongVal] and [DoubleVal] differ here -- `1` vs
     * `1.0` -- which is intentional: the reader can tell at a glance
     * whether a result stayed integer or got promoted to floating point.
     */
    abstract fun display(): String
}

/** A 64-bit signed integer. Produced by integer literals like `42`. */
data class LongVal(val value: Long) : Value() {
    override fun display(): String = value.toString()
}

/** A 64-bit IEEE-754 float. Produced by literals like `3.14`. */
data class DoubleVal(val value: Double) : Value() {
    override fun display(): String = value.toString()
}

/** A boolean. Produced by the keyword literals `true` and `false`. */
data class BoolVal(val value: Boolean) : Value() {
    override fun display(): String = value.toString()
}

/**
 * A user-defined function. We keep a reference to the full [FunDecl] so
 * the evaluator can, at call time, inspect the parameter list and walk
 * the body. Function names live in their own namespace (separate from
 * variables), so [FunVal] never appears in the final output printed by
 * the interpreter -- [display] is only here for diagnostic messages.
 */
data class FunVal(val decl: FunDecl) : Value() {
    override fun display(): String = "<function ${decl.name}>"

    // Overridden so that debug dumps don't expand the whole function body,
    // which the auto-generated data-class toString would do.
    override fun toString(): String = "FunVal(${decl.name}/${decl.params.size})"
}