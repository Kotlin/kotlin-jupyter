package org.jetbrains.kotlinx.jupyter.compiler

import kotlin.script.experimental.api.SourceCode

interface ScriptImportsCollector {
    fun collect(source: SourceCode)
    fun popAddedImports(): List<String>

    object NoOp : ScriptImportsCollector {
        override fun collect(source: SourceCode) {
        }

        override fun popAddedImports(): List<String> = emptyList()
    }
}
