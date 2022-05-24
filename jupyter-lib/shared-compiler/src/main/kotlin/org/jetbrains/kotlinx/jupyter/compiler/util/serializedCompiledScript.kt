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
data class SerializedScriptSource(
    val fileName: String,
    val text: String,
)

@Serializable
data class SerializedCompiledScriptsData(
    val scripts: List<SerializedCompiledScript>,
    val sources: List<SerializedScriptSource>,
) {
    companion object {
        val EMPTY = buildScriptsData {}
    }

    class Builder {
        private val scripts = mutableListOf<SerializedCompiledScript>()
        private val sources = mutableListOf<SerializedScriptSource>()

        fun build() = SerializedCompiledScriptsData(scripts.toList(), sources.toList())

        fun clear() {
            scripts.clear()
            sources.clear()
        }

        fun addData(newData: SerializedCompiledScriptsData) {
            scripts.addAll(newData.scripts)
            sources.addAll(newData.sources)
        }

        fun addCompiledScript(script: SerializedCompiledScript) {
            scripts.add(script)
        }

        fun addSource(source: SerializedScriptSource) {
            sources.add(source)
        }
    }
}

fun buildScriptsData(action: SerializedCompiledScriptsData.Builder.() -> Unit): SerializedCompiledScriptsData {
    return SerializedCompiledScriptsData.Builder().apply(action).build()
}

@Serializable
class EvaluatedSnippetMetadata(
    val newClasspath: Classpath = emptyList(),
    val newSources: Classpath = emptyList(),
    val compiledData: SerializedCompiledScriptsData = SerializedCompiledScriptsData.EMPTY,
    val newImports: List<String> = emptyList(),
    val evaluatedVariablesState: Map<String, String?> = mutableMapOf()
) {
    companion object {
        val EMPTY = EvaluatedSnippetMetadata()
    }
}
