package io.github.lucasvallejoo.toylang.runtime

import io.github.lucasvallejoo.toylang.ast.FunDecl

/**
 * The interpreter's storage for variables and functions at runtime.
 *
 * Toylang keeps two independent namespaces:
 *  - **Variables** — top-level assignments and function-local names.
 *    Top-level variables are kept in insertion order so that the final
 *    output lists them in the order the user first defined them.
 *  - **Functions** — only `fun` declarations, and only at the top
 *    level (the parser already rejects nested functions).
 *
 * Function calls push a **local frame** onto a stack. Inside the call:
 *  - Reading a variable looks at the current local frame first, then
 *    falls back to globals if the name is not local.
 *  - Writing a variable always targets the current local frame.
 *
 * This is Python's default scoping rule without `global` / `nonlocal`:
 * a function can *read* the outer world but not *mutate* it. None of the
 * sample programs in the brief write to globals from inside a function,
 * so this restriction costs nothing and keeps reasoning simple.
 */
class Environment {

    // LinkedHashMap preserves insertion order: the interpreter uses this
    // to print top-level variables in the order they were first assigned.
    private val globals: LinkedHashMap<String, Value> = linkedMapOf()

    private val functions: MutableMap<String, FunVal> = hashMapOf()

    // Stack of local frames. The topmost frame belongs to the function
    // call currently in progress; if the stack is empty we are at the
    // top level, and writes go to globals.
    private val frames: ArrayDeque<MutableMap<String, Value>> = ArrayDeque()

    // -------- Variables --------

    /**
     * Look up a variable by name. Returns `null` if neither the current
     * local frame nor the globals have it -- the caller is responsible
     * for turning that into a proper runtime error with a source position.
     */
    fun getVariable(name: String): Value? {
        val frame = frames.lastOrNull()
        if (frame != null && name in frame) return frame[name]
        return globals[name]
    }

    /**
     * Assign [value] to [name]. Inside a function call this writes to the
     * local frame (creating the entry if needed); at the top level it
     * writes to the globals, which is what the final printout iterates.
     */
    fun setVariable(name: String, value: Value) {
        val frame = frames.lastOrNull()
        if (frame != null) {
            frame[name] = value
        } else {
            globals[name] = value
        }
    }

    // -------- Functions --------

    /** Register a top-level function declaration. */
    fun defineFunction(decl: FunDecl) {
        functions[decl.name] = FunVal(decl)
    }

    /** Look up a function by name. Returns `null` if no such function. */
    fun getFunction(name: String): FunVal? = functions[name]

    // -------- Frame management --------

    /**
     * Run [block] with [frame] pushed as the active local scope. The
     * frame is always popped on the way out -- whether [block] returns
     * normally, throws a runtime error, or unwinds a `return` signal --
     * so push/pop can never leak.
     */
    fun <T> inFrame(frame: MutableMap<String, Value>, block: () -> T): T {
        frames.addLast(frame)
        try {
            return block()
        } finally {
            frames.removeLast()
        }
    }

    // -------- Output --------

    /**
     * The top-level variables, in the order they were first assigned.
     * This is what the interpreter prints when the program finishes.
     */
    fun topLevelVariables(): List<Pair<String, Value>> =
        globals.entries.map { it.key to it.value }
}