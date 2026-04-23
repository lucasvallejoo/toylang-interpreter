package io.github.lucasvallejoo.toylang

import io.github.lucasvallejoo.toylang.lexer.Lexer
import io.github.lucasvallejoo.toylang.lexer.LexerException
import kotlin.system.exitProcess

/**
 * Entry point of the Toylang interpreter.
 *
 * Reads a complete source program from standard input, runs it through the
 * lexer, and prints the resulting token stream to standard output. Parsing
 * and evaluation will be layered on top in the coming phases; until then
 * this command acts as a "tokens viewer", which is useful for debugging the
 * sample programs against the lexer.
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
        for (token in tokens) {
            println(token)
        }
    } catch (e: LexerException) {
        System.err.println("toylang: ${e.message}")
        exitProcess(1)
    }
}
