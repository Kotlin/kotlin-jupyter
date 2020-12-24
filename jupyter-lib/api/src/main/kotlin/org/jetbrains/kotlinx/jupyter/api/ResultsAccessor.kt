package org.jetbrains.kotlinx.jupyter.api

/**
 * Execution results accessor interface
 */
fun interface ResultsAccessor {
    operator fun get(i: Int): Any?
}
