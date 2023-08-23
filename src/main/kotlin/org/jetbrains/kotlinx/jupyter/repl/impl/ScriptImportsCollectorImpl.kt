package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlinx.jupyter.compiler.ScriptDataCollector
import org.jetbrains.kotlinx.jupyter.compiler.ScriptImportsCollector

class ScriptImportsCollectorImpl : ScriptImportsCollector {
    private val addedImports = mutableListOf<String>()

    override fun collect(scriptInfo: ScriptDataCollector.ScriptInfo) {
        val source = scriptInfo.source
        if (source !is KtFileScriptSource) return
        source.ktFile.importDirectives.mapNotNullTo(addedImports) {
            it.importPath?.pathStr
        }
    }

    override fun popAddedImports(): List<String> {
        val res = addedImports.toList()
        addedImports.clear()
        return res
    }
}
