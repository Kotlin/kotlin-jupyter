package org.jetbrains.kotlinx.jupyter.compiler.util

import kotlinx.serialization.Serializable

typealias Classpath = List<String>

@Serializable
data class SerializedCompiledScript(
    val fileName: String,
    val data: String,
    val isImplicitReceiver: Boolean,
)

@Serializable
data class SerializedCompiledScriptsData(
    val scripts: List<SerializedCompiledScript>
) {
    companion object {
        val EMPTY = SerializedCompiledScriptsData(emptyList())
    }
}

@Serializable
data class SerializedVariablesState(
    val type: String = "",
    val value: String? = null,
    val isContainer: Boolean = false
) {
    val fieldDescriptor: MutableMap<String, SerializedVariablesState?> = mutableMapOf()
}

@Serializable
class EvaluatedSnippetMetadata(
    val newClasspath: Classpath = emptyList(),
    val compiledData: SerializedCompiledScriptsData = SerializedCompiledScriptsData.EMPTY,
    val newImports: List<String> = emptyList(),
    val evaluatedVariablesState: Map<String, SerializedVariablesState> = emptyMap()
) {
    companion object {
        val EMPTY = EvaluatedSnippetMetadata()
    }
}
