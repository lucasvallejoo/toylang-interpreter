package io.github.lucasvallejoo.toylang

import io.github.lucasvallejoo.toylang.ast.sexpr
import io.github.lucasvallejoo.toylang.lexer.Lexer
import io.github.lucasvallejoo.toylang.lexer.LexerException
import io.github.lucasvallejoo.toylang.parser.Parser
import io.github.lucasvallejoo.toylang.parser.ParserException
import io.github.lucasvallejoo.toylang.runtime.EvaluationException
import io.github.lucasvallejoo.toylang.runtime.Evaluator
import kotlin.system.exitProcess

private const val USAGE = """usage: toylang [--debug] [--help]

  --debug   Dump the parsed AST (as s-expressions) to stderr before executing.
  --help    Show this message and exit.

The program source is read from standard input; final top-level variable
values are written to standard output, one per line."""

/**
 * Entry point of the Toylang interpreter.
 *
 * Reads a complete source program from standard input, runs it through
 * the three phases -- lexer, parser, evaluator -- and prints the final
 * values of the top-level variables on standard output, one per line,
 * in the order they were first assigned.
 *
 * Supported flags:
 *  - `--debug`  dumps the AST (as s-expressions) to standard error
 *               after parsing, so the user can inspect the exact shape
 *               the parser produced before execution begins.
 *  - `--help`   prints a short usage message and exits with status 0.
 *
 * Any unrecognised option aborts the run with status 2 (a usage error,
 * distinct from the status 1 used for program errors).
 */
fun main(args: Array<String>) {
    if ("--help" in args) {
        println(USAGE)
        return
    }

    val known = setOf("--debug", "--help")
    val unknown = args.filter { it.startsWith("--") && it !in known }
    if (unknown.isNotEmpty()) {
        System.err.println("toylang: unknown option(s): ${unknown.joinToString(" ")}")
        System.err.println(USAGE)
        exitProcess(2)
    }

    val debug = "--debug" in args
    val source = System.`in`.bufferedReader().readText()

    if (source.isBlank()) {
        System.err.println("toylang: no input received on stdin")
        return
    }

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
        reportError(source, e.message ?: "lexer error", e.line, e.column)
        exitProcess(1)
    } catch (e: ParserException) {
        reportError(source, e.message ?: "parse error", e.line, e.column)
        exitProcess(1)
    } catch (e: EvaluationException) {
        reportError(source, e.message ?: "runtime error", e.line, e.column)
        exitProcess(1)
    }
}

/**
 * Pretty-print a diagnostic with a caret under the offending character.
 *
 *  toylang: <message>
 *    at line <N>, column <M>
 *
 *      <source line>
 *      <spaces>^
 *
 * The source is split on '\n' only; CR characters inside the line are
 * rendered literally. Tabs are rewritten to a single space so the caret
 * stays aligned with the 1-column-per-tab count the lexer uses.
 */
private fun reportError(source: String, message: String, line: Int, column: Int) {
    System.err.println("toylang: $message")
    System.err.println("  at line $line, column $column")

    val lines = source.split("\n")
    if (line in 1..lines.size) {
        val rendered = lines[line - 1].replace("\t", " ")
        val caretOffset = (column - 1).coerceAtLeast(0)
        System.err.println()
        System.err.println("    $rendered")
        System.err.println("    ${" ".repeat(caretOffset)}^")
    }
}