package io.github.lucasvallejoo.toylang.ast

/**
 * The root of the AST: a program is an ordered list of top-level
 * statements. Function declarations and "ordinary" simple statements share
 * the same list, which matches how the sample programs in the brief are
 * written (a function body can be declared between two assignments).
 *
 * The evaluator will walk [statements] in order, with one subtlety: at
 * program start, all [FunDecl]s are bound first so that later code can
 * call functions that are defined further down in the source.
 */
data class Program(val statements: List<Stmt>)
