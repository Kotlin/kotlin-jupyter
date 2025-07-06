package org.jetbrains.kotlinx.jupyter.api

/**
 * This is used to represent whether the underlying REPL is running in either K1 or K2 mode.
 */
enum class ReplCompilerMode {
    K1,
    K2,
    ;

    companion object
}

val ReplCompilerMode.Companion.DEFAULT get() = replCompilerModeFromEnvironment ?: ReplCompilerMode.K1

private val replCompilerModeFromEnvironment by lazy {
    val envValue = System.getenv("KOTLIN_JUPYTER_REPL_COMPILER_MODE") ?: return@lazy null
    ReplCompilerMode.values().find { it.name.equals(envValue, ignoreCase = true) }
}
