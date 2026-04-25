package io.github.lucasvallejoo.toylang.runtime

import io.github.lucasvallejoo.toylang.lexer.Lexer
import io.github.lucasvallejoo.toylang.parser.Parser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class EvaluatorTest {

    private fun run(source: String): Environment {
        val tokens = Lexer(source).tokenize()
        val program = Parser(tokens).parse()
        return Evaluator().run(program)
    }

    private fun longVar(env: Environment, name: String): Long =
        (env.getVariable(name) as LongVal).value

    private fun doubleVar(env: Environment, name: String): Double =
        (env.getVariable(name) as DoubleVal).value

    private fun boolVar(env: Environment, name: String): Boolean =
        (env.getVariable(name) as BoolVal).value

    private fun stringVar(env: Environment, name: String): String =
        (env.getVariable(name) as StringVal).value

    /**
     * Run [block] with stdout temporarily redirected to a buffer, and
     * return whatever was written. Used to assert against the side
     * effects of the `print` built-in. JUnit Jupiter executes tests
     * sequentially by default, so swapping `System.out` is safe.
     */
    private fun captureStdout(block: () -> Unit): String {
        val buf = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(buf))
        try {
            block()
        } finally {
            System.out.flush()
            System.setOut(original)
        }
        return buf.toString()
    }

    // -------------------- Literals and simple assignment --------------------

    @Test
    fun `integer literal is stored as a LongVal`() {
        val env = run("x = 42")
        assertEquals(42L, longVar(env, "x"))
    }

    @Test
    fun `double literal is stored as a DoubleVal`() {
        val env = run("x = 3.14")
        assertEquals(3.14, doubleVar(env, "x"), 0.0)
    }

    @Test
    fun `boolean literals are stored as BoolVal`() {
        val env = run("t = true\nf = false")
        assertTrue(boolVar(env, "t"))
        assertTrue(!boolVar(env, "f"))
    }

    @Test
    fun `reassignment overwrites the previous value`() {
        val env = run("x = 1\nx = 2")
        assertEquals(2L, longVar(env, "x"))
    }

    @Test
    fun `top-level variables preserve insertion order`() {
        val env = run("b = 1\na = 2\nc = 3")
        val names = env.topLevelVariables().map { it.first }
        assertEquals(listOf("b", "a", "c"), names)
    }

    // -------------------- Arithmetic --------------------

    @Test
    fun `integer division truncates`() {
        val env = run("x = 7 / 2")
        assertEquals(3L, longVar(env, "x"))
    }

    @Test
    fun `float division keeps fractional part`() {
        val env = run("x = 7.0 / 2")
        assertEquals(3.5, doubleVar(env, "x"), 0.0)
    }

    @Test
    fun `mixing int and double promotes to double`() {
        val env = run("x = 2 + 0.5")
        assertEquals(2.5, doubleVar(env, "x"), 0.0)
    }

    @Test
    fun `modulo works on integers`() {
        val env = run("x = 10 % 3")
        assertEquals(1L, longVar(env, "x"))
    }

    @Test
    fun `unary minus negates a number`() {
        val env = run("x = -5\ny = -3.5")
        assertEquals(-5L, longVar(env, "x"))
        assertEquals(-3.5, doubleVar(env, "y"), 0.0)
    }

    @Test
    fun `operator precedence matches math conventions`() {
        val env = run("x = 2 + 3 * 4")
        assertEquals(14L, longVar(env, "x"))
    }

    @Test
    fun `integer power keeps a Long result`() {
        val env = run("x = 2 ** 10")
        assertEquals(1024L, longVar(env, "x"))
    }

    @Test
    fun `power with a non-negative Long exponent stays in integer arithmetic`() {
        val env = run("x = 3 ** 0\ny = 5 ** 1")
        assertEquals(1L, longVar(env, "x"))
        assertEquals(5L, longVar(env, "y"))
    }

    @Test
    fun `power with a negative exponent promotes to Double`() {
        // 2 ** -1 cannot honestly be a Long, so we promote.
        val env = run("x = 2 ** -1")
        assertEquals(0.5, doubleVar(env, "x"), 0.0)
    }

    @Test
    fun `power with a Double base produces a Double`() {
        val env = run("x = 2.0 ** 3")
        assertEquals(8.0, doubleVar(env, "x"), 0.0)
    }

    @Test
    fun `power is right-associative at runtime`() {
        // 2 ** 3 ** 2  =  2 ** 9  =  512
        val env = run("x = 2 ** 3 ** 2")
        assertEquals(512L, longVar(env, "x"))
    }

    @Test
    fun `power binds tighter than unary minus at runtime`() {
        // -2 ** 2  =  -(2 ** 2)  =  -4
        val env = run("x = -2 ** 2")
        assertEquals(-4L, longVar(env, "x"))
    }

    @Test
    fun `power on a string is a type error`() {
        val e = assertThrows<EvaluationException> { run("x = \"hi\" ** 2") }
        assertTrue(e.message!!.contains("number"))
    }

    @Test
    fun `parentheses override precedence`() {
        val env = run("x = (2 + 3) * 4")
        assertEquals(20L, longVar(env, "x"))
    }

    // -------------------- Comparisons and equality --------------------

    @Test
    fun `numeric comparisons across int and double`() {
        val env = run("a = 1 < 2.0\nb = 2.0 == 2")
        assertTrue(boolVar(env, "a"))
        assertTrue(boolVar(env, "b"))
    }

    @Test
    fun `equality between bool and number is false but not an error`() {
        val env = run("x = 1 == true")
        assertTrue(!boolVar(env, "x"))
    }

    // -------------------- Logical operators and short-circuit --------------------

    @Test
    fun `logical not inverts a boolean`() {
        val env = run("x = not true\ny = not false")
        assertTrue(!boolVar(env, "x"))
        assertTrue(boolVar(env, "y"))
    }

    @Test
    fun `and short-circuits when the left is false`() {
        // If the right side were evaluated, `10 / 0` would blow up.
        val env = run("x = false and (10 / 0 == 0)")
        assertTrue(!boolVar(env, "x"))
    }

    @Test
    fun `or short-circuits when the left is true`() {
        val env = run("x = true or (10 / 0 == 0)")
        assertTrue(boolVar(env, "x"))
    }

    // -------------------- Control flow --------------------

    @Test
    fun `if picks the then branch when the condition is true`() {
        val env = run("y = 0\nif true then y = 1 else y = 2")
        assertEquals(1L, longVar(env, "y"))
    }

    @Test
    fun `if picks the else branch when the condition is false`() {
        val env = run("y = 0\nif false then y = 1 else y = 2")
        assertEquals(2L, longVar(env, "y"))
    }

    @Test
    fun `while loop updates a counter until the condition is false`() {
        val env = run("i = 0\nwhile i < 5 do i = i + 1")
        assertEquals(5L, longVar(env, "i"))
    }

    @Test
    fun `while body with multiple statements runs each on every iteration`() {
        val env = run(
            """
            i = 0
            sum = 0
            while i < 4 do sum = sum + i, i = i + 1
            """.trimIndent()
        )
        assertEquals(6L, longVar(env, "sum"))
        assertEquals(4L, longVar(env, "i"))
    }

    // -------------------- Functions --------------------

    @Test
    fun `function call returns a value`() {
        val env = run(
            """
            fun double(x) { return x * 2 }
            y = double(21)
            """.trimIndent()
        )
        assertEquals(42L, longVar(env, "y"))
    }

    @Test
    fun `function can read globals but cannot mutate them`() {
        val env = run(
            """
            g = 10
            fun read_and_shadow(x) { g = x, return g }
            y = read_and_shadow(99)
            """.trimIndent()
        )
        assertEquals(10L, longVar(env, "g"))
        assertEquals(99L, longVar(env, "y"))
    }

    @Test
    fun `function declared later can be called from earlier function`() {
        val env = run(
            """
            fun a(x) { return b(x) + 1 }
            fun b(x) { return x * 2 }
            y = a(3)
            """.trimIndent()
        )
        assertEquals(7L, longVar(env, "y"))
    }

    @Test
    fun `recursive factorial matches sample 6`() {
        val env = run(
            """
            fun fact_rec(n) {
              if n == 1 then return 1 else return n * fact_rec(n - 1)
            }
            y = fact_rec(5)
            """.trimIndent()
        )
        assertEquals(120L, longVar(env, "y"))
    }

    // -------------------- Runtime errors --------------------

    @Test
    fun `undefined variable raises EvaluationException`() {
        val e = assertThrows<EvaluationException> { run("x = y") }
        assertTrue(e.message!!.contains("undefined variable 'y'"))
    }

    @Test
    fun `undefined function raises EvaluationException`() {
        val e = assertThrows<EvaluationException> { run("x = ghost(1)") }
        assertTrue(e.message!!.contains("undefined function 'ghost'"))
    }

    @Test
    fun `wrong arity raises EvaluationException`() {
        val source = """
            fun f(a, b) { return a + b }
            x = f(1)
        """.trimIndent()
        val e = assertThrows<EvaluationException> { run(source) }
        assertTrue(e.message!!.contains("expects 2 argument"))
    }

    @Test
    fun `integer division by zero raises EvaluationException`() {
        val e = assertThrows<EvaluationException> { run("x = 1 / 0") }
        assertTrue(e.message!!.contains("division by zero"))
    }

    @Test
    fun `modulo by zero raises EvaluationException`() {
        val e = assertThrows<EvaluationException> { run("x = 1 % 0") }
        assertTrue(e.message!!.contains("division by zero"))
    }

    @Test
    fun `arithmetic on a boolean raises a type error`() {
        val e = assertThrows<EvaluationException> { run("x = true + 1") }
        assertTrue(e.message!!.contains("number"))
    }

    @Test
    fun `if condition must be a boolean`() {
        val e = assertThrows<EvaluationException> { run("if 1 then x = 1 else x = 2") }
        assertTrue(e.message!!.contains("boolean"))
    }

    @Test
    fun `falling off a function without return is an error`() {
        val source = """
            fun nothing(x) { x = x + 1 }
            y = nothing(0)
        """.trimIndent()
        val e = assertThrows<EvaluationException> { run(source) }
        assertTrue(e.message!!.contains("without returning"))
    }

    // -------------------- Strings --------------------

    @Test
    fun `string literal is stored as a StringVal with the decoded value`() {
        val env = run("s = \"hello\"")
        assertEquals("hello", stringVar(env, "s"))
    }

    @Test
    fun `escaped string literal stores real characters, not source escapes`() {
        val env = run("s = \"line1\\nline2\\ttabbed\"")
        assertEquals("line1\nline2\ttabbed", stringVar(env, "s"))
    }

    @Test
    fun `string concatenation with + produces a new string`() {
        val env = run("s = \"foo\" + \"bar\"")
        assertEquals("foobar", stringVar(env, "s"))
    }

    @Test
    fun `string equality compares character by character`() {
        val env = run(
            """
            a = "abc" == "abc"
            b = "abc" == "abd"
            c = "abc" != "abd"
            """.trimIndent()
        )
        assertTrue(boolVar(env, "a"))
        assertTrue(!boolVar(env, "b"))
        assertTrue(boolVar(env, "c"))
    }

    @Test
    fun `string compared to a non-string is never equal but not an error`() {
        val env = run(
            """
            a = "1" == 1
            b = "true" == true
            """.trimIndent()
        )
        assertTrue(!boolVar(env, "a"))
        assertTrue(!boolVar(env, "b"))
    }

    @Test
    fun `adding a string to a number is a type error`() {
        // No implicit coercion: the user must convert explicitly. Until we
        // expose a `str()` built-in, mixing produces a clear error.
        val e = assertThrows<EvaluationException> { run("s = \"hi\" + 1") }
        assertTrue(e.message!!.contains("number") || e.message!!.contains("string"))
    }

    @Test
    fun `arithmetic operators other than plus reject strings`() {
        val e = assertThrows<EvaluationException> { run("s = \"a\" * 3") }
        assertTrue(e.message!!.contains("number"))
    }

    @Test
    fun `display form of a top-level string variable is quoted`() {
        // The end-user contract: at the end of the program, a string variable
        // is printed in source-literal form so it cannot be confused with an
        // identifier. Re-escaping internal special characters is part of
        // that contract.
        val env = run("s = \"he said \\\"hi\\\"\"")
        val display = (env.getVariable("s") as StringVal).display()
        assertEquals("\"he said \\\"hi\\\"\"", display)
    }

    // -------------------- print built-in --------------------

    @Test
    fun `print writes a string argument without quotes`() {
        val out = captureStdout { run("print(\"hello\")") }
        assertEquals("hello\n", out)
    }

    @Test
    fun `print writes a number argument in display form`() {
        val out = captureStdout { run("print(42)") }
        assertEquals("42\n", out)
    }

    @Test
    fun `print joins multiple arguments with single spaces`() {
        val out = captureStdout { run("print(\"x =\", 1, true)") }
        assertEquals("x = 1 true\n", out)
    }

    @Test
    fun `print with no arguments emits just a newline`() {
        val out = captureStdout { run("print()") }
        assertEquals("\n", out)
    }

    @Test
    fun `print evaluates its arguments in the caller's scope`() {
        // Verifies that `n` resolves to the global, not to anything else.
        val out = captureStdout {
            run(
                """
                n = 7
                print("n is", n + 1)
                """.trimIndent()
            )
        }
        assertEquals("n is 8\n", out)
    }

    @Test
    fun `print returns LongVal(0) so it is legal in expression position`() {
        // Capture and discard stdout; we only care about the assignment.
        val env = captureStdoutEnv("r = print(\"hi\")")
        assertEquals(0L, longVar(env, "r"))
    }

    @Test
    fun `user-defined print shadows the built-in`() {
        // If the user declares their own `print`, calls resolve to it
        // before the built-in is even consulted -- the built-in is only
        // a fallback for unknown names.
        val env = run(
            """
            fun print(x) { return x + 1 }
            y = print(41)
            """.trimIndent()
        )
        assertEquals(42L, longVar(env, "y"))
    }

    @Test
    fun `print works inside a loop and a function body`() {
        // Function and while bodies separate statements with commas, not
        // newlines -- same rule we locked down in phase 2.
        val out = captureStdout {
            run(
                """
                fun greet(name) { print("hi", name), return 0 }
                i = 0
                while i < 3 do greet("user"), i = i + 1
                """.trimIndent()
            )
        }
        assertEquals("hi user\nhi user\nhi user\n", out)
    }

    /**
     * Variant of [run] used by tests that both capture stdout and inspect
     * the resulting environment. Returns the [Environment]; the captured
     * output is silently discarded, matching the use case of `r = print(...)`
     * where we only care about `r`.
     */
    private fun captureStdoutEnv(source: String): Environment {
        var env: Environment? = null
        captureStdout { env = run(source) }
        return env!!
    }

    // -------------------- Brief samples (end-to-end) --------------------

    @Test
    fun `sample 1 - two assignments with a nested expression`() {
        val env = run(
            """
            x = 1
            y = 2 * (x + 5)
            """.trimIndent()
        )
        assertEquals(1L, longVar(env, "x"))
        assertEquals(12L, longVar(env, "y"))
    }

    @Test
    fun `sample 2 - simple arithmetic chain`() {
        val env = run(
            """
            a = 10
            b = a - 3
            c = b * 2
            """.trimIndent()
        )
        assertEquals(10L, longVar(env, "a"))
        assertEquals(7L, longVar(env, "b"))
        assertEquals(14L, longVar(env, "c"))
    }

    @Test
    fun `sample 3 - while with nested if-else and trailing update`() {
        val env = run(
            """
            x = 0
            y = 0
            while x < 5 do if x == 1 then y = 10 else y = y + 1, x = x + 1
            """.trimIndent()
        )
        assertEquals(5L, longVar(env, "x"))
        // Trace: x=0 -> y=1; x=1 -> y=10; x=2 -> y=11; x=3 -> y=12; x=4 -> y=13.
        assertEquals(13L, longVar(env, "y"))
    }

    @Test
    fun `sample 4 - if-else without a loop`() {
        val env = run(
            """
            x = 7
            if x > 5 then y = 1 else y = 0
            """.trimIndent()
        )
        assertEquals(1L, longVar(env, "y"))
    }

    @Test
    fun `sample 5 - function with two parameters`() {
        val env = run(
            """
            fun add(a, b) { return a + b }
            r = add(3, 4)
            """.trimIndent()
        )
        assertEquals(7L, longVar(env, "r"))
    }

    @Test
    fun `sample 6 - recursive factorial`() {
        val env = run(
            """
            fun fact_rec(n) {
              if n == 1 then return 1 else return n * fact_rec(n - 1)
            }
            y = fact_rec(6)
            """.trimIndent()
        )
        assertEquals(720L, longVar(env, "y"))
    }
}