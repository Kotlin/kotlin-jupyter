package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScriptsData

data class InternalEvalResult(
    val result: FieldValue,
    val scriptInstance: Any,
    val compiledData: SerializedCompiledScriptsData? = null
)
