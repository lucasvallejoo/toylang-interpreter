package io.github.lucasvallejoo.toylang

import io.github.lucasvallejoo.toylang.ast.sexpr
import io.github.lucasvallejoo.toylang.lexer.Lexer
import io.github.lucasvallejoo.toylang.lexer.LexerException
import io.github.lucasvallejoo.toylang.parser.Parser
import io.github.lucasvallejoo.toylang.parser.ParserException
import kotlin.system.exitProcess

/**
 * Entry point of the Toylang interpreter.
 *
 * Reads a complete source program from standard input, runs it through the
 * lexer and parser, and prints the resulting AST as an s-expression dump
 * on standard output. Evaluation will be layered on top in the coming
 * phase; until then this command acts as a "parse tree viewer", useful for
 * debugging sample programs against the front end.
 *
 * Diagnostic messages (errors, notices) are written to standard error, so
 * standard output stays clean and machine-friendly.
 */
fun main() {
    val source = System.`in`.bufferedReader().readText()

    if (source.isBlank()) {
        System.err.println("toylang: no input received on stdin")
        return
    }

    try {
        val tokens = Lexer(source).tokenize()
        val program = Parser(tokens).parse()
        println(program.sexpr())
    } catch (e: LexerException) {
        System.err.println("toylang: ${e.message}")
        exitProcess(1)
    } catch (e: ParserException) {
        System.err.println("toylang: ${e.message}")
        exitProcess(1)
    }
}
