package org.jetbrains.kotlinx.jupyter.api;

/**
 * This is used to represent whether the underlying REPL is running in either K1 or K2 mode.
 */
enum class ReplCompilerMode {
    K1,
    K2,
    ;

    fun isK2() = (this == K2)

    companion object
}

val ReplCompilerMode.Companion.DEFAULT get() = ReplCompilerMode.K1
