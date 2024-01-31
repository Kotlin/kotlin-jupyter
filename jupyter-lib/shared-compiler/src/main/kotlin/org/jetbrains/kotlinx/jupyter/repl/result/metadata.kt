package org.jetbrains.kotlinx.jupyter.repl.result

fun buildScriptsData(action: SerializedCompiledScriptsData.Builder.() -> Unit): SerializedCompiledScriptsData {
    return SerializedCompiledScriptsData.Builder().apply(action).build()
}
