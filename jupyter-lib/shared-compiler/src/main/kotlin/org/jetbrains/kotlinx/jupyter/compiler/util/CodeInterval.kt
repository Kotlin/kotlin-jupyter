package org.jetbrains.kotlinx.jupyter.compiler.util

data class CodeInterval(
    /**
     * Inclusive
     */
    val from: Int,
    /**
     * Exclusive
     */
    val to: Int,
) {
    operator fun contains(position: Int): Boolean {
        return position in from until to
    }
}
