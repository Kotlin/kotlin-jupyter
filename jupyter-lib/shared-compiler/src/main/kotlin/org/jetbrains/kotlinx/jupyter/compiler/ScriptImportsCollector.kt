package org.jetbrains.kotlinx.jupyter.compiler

interface ScriptImportsCollector : ScriptDataCollector {
    fun popAddedImports(): List<String>
}
