package org.jetbrains.kotlinx.jupyter.api

/**
 * Evaluates code. Returns rendered result or null in case of the error.
 */
fun interface CodeEvaluator {
    fun eval(code: Code): Any?
}
