package io.github.lucasvallejoo.toylang.parser

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
import io.github.lucasvallejoo.toylang.ast.Unary
import io.github.lucasvallejoo.toylang.ast.UnaryOp
import io.github.lucasvallejoo.toylang.ast.VarRef
import io.github.lucasvallejoo.toylang.ast.While
import io.github.lucasvallejoo.toylang.lexer.Lexer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ParserTest {

    private fun parse(source: String): Program =
        Parser(Lexer(source).tokenize()).parse()

    /** Convenience: pull the RHS expression out of a single assignment. */
    private fun parseAssignmentValue(source: String): Expr {
        val program = parse("x = $source")
        val a = program.statements.single() as Assignment
        return a.value
    }

    // -------------------- Empty and trivial --------------------

    @Test
    fun `empty input parses to an empty program`() {
        assertEquals(0, parse("").statements.size)
    }

    @Test
    fun `simple assignment becomes an Assignment node`() {
        val program = parse("x = 2")
        val a = program.statements.single() as Assignment
        assertEquals("x", a.name)
        assertEquals(2L, (a.value as NumberLit).value)
    }

    @Test
    fun `multiple top-level statements without explicit separator`() {
        val program = parse("x = 1 y = 2 z = 3")
        assertEquals(3, program.statements.size)
        assertEquals("x", (program.statements[0] as Assignment).name)
        assertEquals("y", (program.statements[1] as Assignment).name)
        assertEquals("z", (program.statements[2] as Assignment).name)
    }

    // -------------------- Arithmetic precedence --------------------

    @Test
    fun `multiplication binds tighter than addition`() {
        val e = parseAssignmentValue("1 + 2 * 3") as Binary
        assertEquals(BinaryOp.PLUS, e.op)
        assertEquals(1L, (e.left as NumberLit).value)
        val right = e.right as Binary
        assertEquals(BinaryOp.TIMES, right.op)
        assertEquals(2L, (right.left as NumberLit).value)
        assertEquals(3L, (right.right as NumberLit).value)
    }

    @Test
    fun `parentheses override precedence`() {
        val e = parseAssignmentValue("(1 + 2) * 3") as Binary
        assertEquals(BinaryOp.TIMES, e.op)
        val left = e.left as Binary
        assertEquals(BinaryOp.PLUS, left.op)
    }

    @Test
    fun `subtraction is left-associative`() {
        val e = parseAssignmentValue("10 - 3 - 2") as Binary
        assertEquals(BinaryOp.MINUS, e.op)
        val left = e.left as Binary
        assertEquals(BinaryOp.MINUS, left.op)
        assertEquals(10L, (left.left as NumberLit).value)
        assertEquals(3L, (left.right as NumberLit).value)
        assertEquals(2L, (e.right as NumberLit).value)
    }

    @Test
    fun `division and modulo share precedence with multiplication`() {
        val e = parseAssignmentValue("10 / 2 % 3 * 4") as Binary
        // ((10 / 2) % 3) * 4
        assertEquals(BinaryOp.TIMES, e.op)
        val left = e.left as Binary
        assertEquals(BinaryOp.MOD, left.op)
        val leftLeft = left.left as Binary
        assertEquals(BinaryOp.DIV, leftLeft.op)
    }

    @Test
    fun `unary minus binds tight enough to survive multiplication`() {
        val e = parseAssignmentValue("-2 * 3") as Binary
        assertEquals(BinaryOp.TIMES, e.op)
        val u = e.left as Unary
        assertEquals(UnaryOp.NEG, u.op)
        assertEquals(2L, (u.operand as NumberLit).value)
    }

    @Test
    fun `double unary minus nests`() {
        val e = parseAssignmentValue("- -5") as Unary
        assertEquals(UnaryOp.NEG, e.op)
        val inner = e.operand as Unary
        assertEquals(UnaryOp.NEG, inner.op)
    }

    // -------------------- Logical and comparison --------------------

    @Test
    fun `and binds tighter than or`() {
        val e = parseAssignmentValue("a or b and c") as Binary
        assertEquals(BinaryOp.OR, e.op)
        val right = e.right as Binary
        assertEquals(BinaryOp.AND, right.op)
    }

    @Test
    fun `not has lower precedence than comparison`() {
        // 'not a == b' parses as 'not (a == b)'.
        val e = parseAssignmentValue("not a == b") as Unary
        assertEquals(UnaryOp.NOT, e.op)
        val inner = e.operand as Binary
        assertEquals(BinaryOp.EQ, inner.op)
    }

    @Test
    fun `comparison chains parse as left-associative binary tree`() {
        // 'a < b < c' parses as '(a < b) < c'. Whether this is meaningful
        // at runtime is the evaluator's problem, not the parser's.
        val e = parseAssignmentValue("a < b < c") as Binary
        assertEquals(BinaryOp.LT, e.op)
        val left = e.left as Binary
        assertEquals(BinaryOp.LT, left.op)
    }

    // -------------------- Literals --------------------

    @Test
    fun `integer and double literals preserve their Kotlin types`() {
        val prog = parse("a = 42 b = 3.14")
        val a = (prog.statements[0] as Assignment).value as NumberLit
        val b = (prog.statements[1] as Assignment).value as NumberLit
        assertEquals(42L, a.value)
        assertEquals(3.14, b.value)
    }

    @Test
    fun `boolean literals become BoolLit`() {
        val prog = parse("t = true f = false")
        val t = (prog.statements[0] as Assignment).value as BoolLit
        val f = (prog.statements[1] as Assignment).value as BoolLit
        assertTrue(t.value)
        assertTrue(!f.value)
    }

    @Test
    fun `bare identifier on the RHS becomes a VarRef`() {
        val a = parse("x = y").statements.single() as Assignment
        val ref = a.value as VarRef
        assertEquals("y", ref.name)
    }

    // -------------------- Control flow --------------------

    @Test
    fun `if then else wraps both branches in a single simple stmt`() {
        val program = parse("if x > 0 then y = 1 else y = 0")
        val ifs = program.statements.single() as If
        val cond = ifs.condition as Binary
        assertEquals(BinaryOp.GT, cond.op)
        assertEquals("y", (ifs.thenBranch as Assignment).name)
        assertEquals("y", (ifs.elseBranch as Assignment).name)
    }

    @Test
    fun `while loop with a single-statement body`() {
        val program = parse("while x < 3 do x = x + 1")
        val w = program.statements.single() as While
        val cond = w.condition as Binary
        assertEquals(BinaryOp.LT, cond.op)
        assertEquals(1, w.body.size)
        assertEquals("x", (w.body[0] as Assignment).name)
    }

    @Test
    fun `while loop with a comma-separated multi-statement body`() {
        val program = parse("while x < 3 do a = 1, b = 2, c = 3")
        val w = program.statements.single() as While
        assertEquals(3, w.body.size)
        assertEquals("a", (w.body[0] as Assignment).name)
        assertEquals("b", (w.body[1] as Assignment).name)
        assertEquals("c", (w.body[2] as Assignment).name)
    }

    @Test
    fun `return statement carries its value expression`() {
        val program = parse("fun f() { return 42 }")
        val f = program.statements.single() as FunDecl
        val r = f.body.single() as Return
        assertEquals(42L, (r.value as NumberLit).value)
    }

    // -------------------- Functions --------------------

    @Test
    fun `zero-argument function declaration`() {
        val f = parse("fun g() { return 1 }").statements.single() as FunDecl
        assertEquals("g", f.name)
        assertEquals(0, f.params.size)
    }

    @Test
    fun `multi-argument function declaration`() {
        val f = parse("fun h(a, b, c) { return a }").statements.single() as FunDecl
        assertEquals(listOf("a", "b", "c"), f.params)
    }

    @Test
    fun `function call in an expression position`() {
        val a = parse("r = fact(5)").statements.single() as Assignment
        val call = a.value as Call
        assertEquals("fact", call.callee)
        assertEquals(1, call.args.size)
        assertEquals(5L, (call.args[0] as NumberLit).value)
    }

    @Test
    fun `call with complex argument expressions`() {
        val a = parse("r = add(1 + 2, 3 * 4)").statements.single() as Assignment
        val call = a.value as Call
        assertEquals(2, call.args.size)
        assertEquals(BinaryOp.PLUS, (call.args[0] as Binary).op)
        assertEquals(BinaryOp.TIMES, (call.args[1] as Binary).op)
    }

    @Test
    fun `bare call is allowed as an expression statement`() {
        val stmt = parse("f(1)").statements.single() as ExprStmt
        val call = stmt.expr as Call
        assertEquals("f", call.callee)
    }

    @Test
    fun `recursive function declaration matches sample 6`() {
        val source = "fun fact_rec(n) { if n <= 0 then return 1 else return n * fact_rec(n - 1) }"
        val f = parse(source).statements.single() as FunDecl
        assertEquals("fact_rec", f.name)
        assertEquals(listOf("n"), f.params)
        val ifs = f.body.single() as If
        val elseRet = ifs.elseBranch as Return
        val mult = elseRet.value as Binary
        assertEquals(BinaryOp.TIMES, mult.op)
        val recCall = mult.right as Call
        assertEquals("fact_rec", recCall.callee)
    }

    // -------------------- Error cases --------------------

    @Test
    fun `if without then raises ParserException`() {
        val e = assertThrows<ParserException> { parse("if x > 0 y = 1 else y = 0") }
        assertTrue(e.message!!.contains("then"))
    }

    @Test
    fun `if without else raises ParserException`() {
        assertThrows<ParserException> { parse("if x > 0 then y = 1") }
    }

    @Test
    fun `unclosed paren raises ParserException`() {
        assertThrows<ParserException> { parse("x = (1 + 2") }
    }

    @Test
    fun `function declaration requires braces`() {
        assertThrows<ParserException> { parse("fun f(x) return x") }
    }

    @Test
    fun `nested function declaration is rejected`() {
        // fun inside a while body is not allowed: 'fun' is not a valid
        // start for a simple statement, so the parser falls through to
        // the default error branch.
        assertThrows<ParserException> {
            parse("while true do fun g() { return 1 }")
        }
    }

    @Test
    fun `assignment chain is rejected`() {
        // 'a = b = 0' -- assignment is a statement, not an expression.
        // The parser sees 'a = b', then '= 0' which cannot start or
        // continue anything.
        assertThrows<ParserException> { parse("a = b = 0") }
    }

    // -------------------- Smoke tests from the brief --------------------

    @Test
    fun `sample 1 - two assignments with a nested expression`() {
        val program = parse(
            """
            x = 2
            y = (x + 2) * 2
            """.trimIndent(),
        )
        assertEquals(2, program.statements.size)
        val y = program.statements[1] as Assignment
        val mult = y.value as Binary
        assertEquals(BinaryOp.TIMES, mult.op)
        val plus = mult.left as Binary
        assertEquals(BinaryOp.PLUS, plus.op)
    }

    @Test
    fun `sample 3 - while with nested if-else and trailing update`() {
        // The tricky case: the trailing 'x = x + 1' must end up inside the
        // while loop's body, and the else branch must NOT greedily eat the
        // comma.
        val source = "x = 1 y = 0 while x < 3 do if x == 1 then y = 10 else y = y + 1, x = x + 1"
        val program = parse(source)
        assertEquals(3, program.statements.size)
        val w = program.statements[2] as While
        assertEquals(2, w.body.size)
        val inner = w.body[0] as If
        val elseBranch = inner.elseBranch as Assignment
        // else-branch is just `y = y + 1`, not `y = y + 1, x = x + 1`.
        assertEquals("y", elseBranch.name)
        val plus = elseBranch.value as Binary
        assertEquals(BinaryOp.PLUS, plus.op)
        // The trailing update is the *second* element of the while body.
        val trailing = w.body[1] as Assignment
        assertEquals("x", trailing.name)
    }

    @Test
    fun `sample 6 - function declaration followed by a call`() {
        val source = """
            fun fact_rec(n) { if n <= 0 then return 1 else return n * fact_rec(n - 1) }
            r = fact_rec(5)
        """.trimIndent()
        val program = parse(source)
        assertEquals(2, program.statements.size)
        assertTrue(program.statements[0] is FunDecl)
        val r = program.statements[1] as Assignment
        assertEquals("r", r.name)
        assertTrue(r.value is Call)
    }
}
