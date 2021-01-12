package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScriptsData

data class InternalEvalResult(
    val value: Any?,
    val resultField: String?,
    val compiledData: SerializedCompiledScriptsData? = null,
)
