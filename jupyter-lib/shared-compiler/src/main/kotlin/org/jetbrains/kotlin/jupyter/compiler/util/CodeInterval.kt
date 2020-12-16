package org.jetbrains.kotlin.jupyter.compiler.util

data class CodeInterval(
    /**
     * Inclusive
     */
    val from: Int,

    /**
     * Exclusive
     */
    val to: Int,
)
