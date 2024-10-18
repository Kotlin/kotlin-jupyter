package org.jetbrains.kotlinx.jupyter.api

/**
 * Evaluates code. Ignores the result
 */
fun interface CodeEvaluator {
    fun eval(code: Code)
}
