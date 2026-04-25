package io.github.lucasvallejoo.toylang

import io.github.lucasvallejoo.toylang.ast.sexpr
import io.github.lucasvallejoo.toylang.lexer.Lexer
import io.github.lucasvallejoo.toylang.lexer.LexerException
import io.github.lucasvallejoo.toylang.parser.Parser
import io.github.lucasvallejoo.toylang.parser.ParserException
import io.github.lucasvallejoo.toylang.runtime.EvaluationException
import io.github.lucasvallejoo.toylang.runtime.Evaluator
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

private const val USAGE = """usage: toylang [--debug] [--help] [--repl] [file]

  --debug   Dump the parsed AST (as s-expressions) to stderr before executing.
  --help    Show this message and exit.
  --repl    Start an interactive read-eval-print loop instead of running a
            program. Cannot be combined with a file argument.
  file      Path to a Toylang source file. If omitted, the program is read
            from standard input.

Final top-level variable values are written to standard output, one per
line. Diagnostics (lexer, parser, runtime errors) go to standard error."""

/**
 * Entry point of the Toylang interpreter.
 *
 * Has two top-level modes:
 *  - **Program mode** (default): reads a full program from a file
 *    argument or, if none is given, from standard input. Runs it
 *    through lexer, parser, and evaluator, and prints the final
 *    top-level variables on standard output. Errors at any phase
 *    are formatted with a caret under the offending column.
 *  - **REPL mode** (`--repl`): drops into an interactive loop where
 *    each chunk of input is parsed and executed against a persistent
 *    [Evaluator]. State carries between iterations.
 *
 * Supported flags:
 *  - `--debug`  dumps the AST (as s-expressions) to standard error
 *               after each successful parse, so the user can inspect
 *               the exact shape the parser produced before execution.
 *  - `--help`   prints a short usage message and exits with status 0.
 *  - `--repl`   enters interactive REPL mode (see [runRepl]).
 *
 * The single positional argument, if present, is the path to a source
 * file. Combining it with `--repl` is rejected as a usage error.
 *
 * Exit codes:
 *  - 0  success (or `--help` / clean REPL exit).
 *  - 1  program error (lexer / parser / evaluator / I/O).
 *  - 2  usage error (unknown flag, too many file args, conflicting flags).
 */
fun main(args: Array<String>) {
    if ("--help" in args) {
        println(USAGE)
        return
    }

    val known = setOf("--debug", "--help", "--repl")
    val unknown = args.filter { it.startsWith("--") && it !in known }
    if (unknown.isNotEmpty()) {
        System.err.println("toylang: unknown option(s): ${unknown.joinToString(" ")}")
        System.err.println(USAGE)
        exitProcess(2)
    }

    val positional = args.filter { !it.startsWith("--") }
    if (positional.size > 1) {
        System.err.println("toylang: too many file arguments (expected 0 or 1)")
        System.err.println(USAGE)
        exitProcess(2)
    }

    val debug = "--debug" in args
    val repl = "--repl" in args
    val filename = positional.firstOrNull()

    if (repl && filename != null) {
        System.err.println("toylang: --repl cannot be combined with a file argument")
        exitProcess(2)
    }

    if (repl) {
        runRepl(debug)
        return
    }

    val source = readSource(filename)
    if (source.isBlank()) {
        val origin = filename ?: "stdin"
        System.err.println("toylang: no input from $origin")
        return
    }

    runProgram(source, debug, filename)
}

/**
 * Read program source from [filename] if given, otherwise from stdin.
 * On I/O failure the diagnostic is printed and the process exits with
 * status 1; the function therefore returns only on success.
 */
private fun readSource(filename: String?): String {
    if (filename == null) return System.`in`.bufferedReader().readText()
    return try {
        File(filename).readText()
    } catch (e: IOException) {
        System.err.println("toylang: cannot read '$filename': ${e.message}")
        exitProcess(1)
    }
}

/**
 * Run [source] as a complete Toylang program. Any error from any
 * phase is rendered through [reportError] and aborts with status 1.
 * If [filename] is given, diagnostics show `file.toy:line:col` so an
 * editor can jump straight to the offending location.
 */
private fun runProgram(source: String, debug: Boolean, filename: String? = null) {
    try {
        val tokens = Lexer(source).tokenize()
        val program = Parser(tokens).parse()
        if (debug) {
            System.err.println("--- ast ---")
            System.err.println(program.sexpr())
            System.err.println("-----------")
        }
        val environment = Evaluator().run(program)
        for ((name, value) in environment.topLevelVariables()) {
            println("$name = ${value.display()}")
        }
    } catch (e: LexerException) {
        reportError(source, e.message ?: "lexer error", e.line, e.column, filename)
        exitProcess(1)
    } catch (e: ParserException) {
        reportError(source, e.message ?: "parse error", e.line, e.column, filename)
        exitProcess(1)
    } catch (e: EvaluationException) {
        reportError(source, e.message ?: "runtime error", e.line, e.column, filename)
        exitProcess(1)
    }
}

/**
 * Run an interactive read-eval-print loop.
 *
 * Each line typed by the user is appended to a buffer and the buffer
 * is run through the lexer and parser. Three outcomes:
 *  1. **Parse succeeds** -- the program is executed, the buffer is
 *     cleared, and the prompt returns to `>>> `.
 *  2. **Parser hits unexpected EOF** -- this means the input is
 *     incomplete (open paren, open brace, dangling operator, ...).
 *     The buffer is kept and the prompt switches to `... ` so the
 *     user can finish their statement on the next line. This works
 *     for any partial syntax without us having to track brackets,
 *     because the parser already knows when it has run out of input.
 *  3. **Any other error** -- printed via [reportError] with a caret
 *     under the offending column, the buffer is cleared, and the
 *     loop continues.
 *
 * The same [Evaluator] is reused across iterations, so variables and
 * function definitions persist for the whole session. With [debug]
 * enabled, the AST is dumped to stderr after each successful parse.
 *
 * Meta-commands at the start of a fresh buffer:
 *  - `:q`, `:quit` -- end the session (also achievable via Ctrl-D).
 *  - `:dump`       -- list every top-level variable currently bound.
 */
private fun runRepl(debug: Boolean) {
    println("Toylang REPL. :q to quit, :dump to list variables, Ctrl-D for EOF.")
    val evaluator = Evaluator()
    val reader = System.`in`.bufferedReader()
    val buffer = StringBuilder()

    while (true) {
        // Conventional REPL prompts go to stdout (matches Python, Lua,
        // bash). The sole consumer is the user looking at a terminal,
        // so polluting stdout for piping is a non-issue here.
        System.out.print(if (buffer.isEmpty()) ">>> " else "... ")
        System.out.flush()

        val line = reader.readLine() ?: break  // null = EOF (Ctrl-D)

        // Meta-commands only work at the top of a fresh buffer; if the
        // user is mid-statement, ":dump" is just an identifier.
        if (buffer.isEmpty() && line.startsWith(":")) {
            when (line.trim()) {
                ":q", ":quit" -> return
                ":dump" -> dumpEnvironment(evaluator)
                else -> System.err.println("toylang: unknown command '${line.trim()}'")
            }
            continue
        }

        // Skip empty input on a fresh prompt -- saves a useless parse.
        if (buffer.isEmpty() && line.isBlank()) continue

        if (buffer.isNotEmpty()) buffer.append('\n')
        buffer.append(line)

        val source = buffer.toString()
        try {
            val tokens = Lexer(source).tokenize()
            val program = Parser(tokens).parse()
            if (debug) {
                System.err.println("--- ast ---")
                System.err.println(program.sexpr())
                System.err.println("-----------")
            }
            evaluator.run(program)
            buffer.clear()
        } catch (e: ParserException) {
            // Heuristic: if the parser ran out of input, the user is
            // mid-statement and we should keep buffering. Anything else
            // is a real syntax error, surfaced and the buffer reset.
            if (e.message?.contains("found EOF") == true) continue
            reportError(source, e.message ?: "parse error", e.line, e.column)
            buffer.clear()
        } catch (e: LexerException) {
            reportError(source, e.message ?: "lexer error", e.line, e.column)
            buffer.clear()
        } catch (e: EvaluationException) {
            reportError(source, e.message ?: "runtime error", e.line, e.column)
            buffer.clear()
        }
    }
}

/** Print every top-level variable in insertion order; used by `:dump`. */
private fun dumpEnvironment(evaluator: Evaluator) {
    val vars = evaluator.environment.topLevelVariables()
    if (vars.isEmpty()) {
        println("(no variables)")
        return
    }
    for ((name, value) in vars) println("$name = ${value.display()}")
}

/**
 * Pretty-print a diagnostic with a caret under the offending character.
 *
 *  toylang: <message>
 *    at <file>:<line>:<column>      (when [filename] is given)
 *    at line <N>, column <M>        (when reading from stdin / REPL)
 *
 *      <source line>
 *      <spaces>^
 *
 * The `file:line:col` form is the convention of modern compilers and
 * lets editors / IDEs jump straight to the location.
 *
 * The source is split on '\n' only; CR characters inside the line are
 * rendered literally. Tabs are rewritten to a single space so the caret
 * stays aligned with the 1-column-per-tab count the lexer uses.
 */
private fun reportError(
    source: String,
    message: String,
    line: Int,
    column: Int,
    filename: String? = null,
) {
    System.err.println("toylang: $message")
    if (filename != null) {
        System.err.println("  at $filename:$line:$column")
    } else {
        System.err.println("  at line $line, column $column")
    }

    val lines = source.split("\n")
    if (line in 1..lines.size) {
        val rendered = lines[line - 1].replace("\t", " ")
        val caretOffset = (column - 1).coerceAtLeast(0)
        System.err.println()
        System.err.println("    $rendered")
        System.err.println("    ${" ".repeat(caretOffset)}^")
    }
}
