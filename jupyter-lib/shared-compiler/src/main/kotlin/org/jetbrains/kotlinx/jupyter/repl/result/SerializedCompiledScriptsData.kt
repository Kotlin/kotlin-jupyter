package org.jetbrains.kotlinx.jupyter.repl.result

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.jupyter.repl.SerializedScriptSource

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
