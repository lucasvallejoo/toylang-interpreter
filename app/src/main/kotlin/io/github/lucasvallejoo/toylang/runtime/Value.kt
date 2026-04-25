package io.github.lucasvallejoo.toylang.runtime

import io.github.lucasvallejoo.toylang.ast.FunDecl
import io.github.lucasvallejoo.toylang.ast.toToylangLiteral

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
     * Render the value the way the interpreter should print it in its
     * **end-of-program top-level dump**. Strings are quoted, numbers
     * carry the `Long` / `Double` distinction (`1` vs `1.0`), so that
     * the dump is unambiguous and could be pasted back as source.
     */
    abstract fun display(): String

    /**
     * Render the value the way the **`print` built-in** should write
     * it. Defaults to [display]; the only override is [StringVal],
     * which drops its surrounding quotes here so that
     * `print("hello")` emits `hello`, not `"hello"`. This is the same
     * split Python makes between `repr()` and `str()`.
     */
    open fun asText(): String = display()
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
 * A string value. The stored [value] is the *decoded* string -- escape
 * sequences such as `\n` have already been resolved by the lexer, so the
 * runtime works with real characters, not source-level escapes.
 *
 * [display] re-escapes the string into its source-literal form (with
 * surrounding quotes) so the final printout is unambiguous: a top-level
 * `s = "hello"` shows as `s = "hello"`, not as `s = hello` (which would
 * be indistinguishable from an identifier).
 */
data class StringVal(val value: String) : Value() {
    override fun display(): String = value.toToylangLiteral()
    override fun asText(): String = value
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