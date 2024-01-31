package org.jetbrains.kotlinx.jupyter.repl.result

interface InternalMetadata {
    val compiledData: SerializedCompiledScriptsData
    val newImports: List<String>
}
