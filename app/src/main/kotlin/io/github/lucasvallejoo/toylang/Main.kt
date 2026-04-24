package io.github.lucasvallejoo.toylang

import io.github.lucasvallejoo.toylang.lexer.Lexer
import io.github.lucasvallejoo.toylang.lexer.LexerException
import io.github.lucasvallejoo.toylang.parser.Parser
import io.github.lucasvallejoo.toylang.parser.ParserException
import io.github.lucasvallejoo.toylang.runtime.EvaluationException
import io.github.lucasvallejoo.toylang.runtime.Evaluator
import kotlin.system.exitProcess

/**
 * Entry point of the Toylang interpreter.
 *
 * Reads a complete source program from standard input, runs it through
 * the three phases -- lexer, parser, evaluator -- and prints the final
 * values of the top-level variables on standard output, one per line,
 * in the order they were first assigned.
 *
 * Diagnostic messages (lexer, parser, and runtime errors) are written
 * to standard error so that standard output stays a clean machine-
 * readable dump of the program's result. On any error the process
 * exits with a non-zero status.
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
        val environment = Evaluator().run(program)
        for ((name, value) in environment.topLevelVariables()) {
            println("$name = ${value.display()}")
        }
    } catch (e: LexerException) {
        System.err.println("toylang: ${e.message}")
        exitProcess(1)
    } catch (e: ParserException) {
        System.err.println("toylang: ${e.message}")
        exitProcess(1)
    } catch (e: EvaluationException) {
        System.err.println("toylang: ${e.message}")
        exitProcess(1)
    }
}