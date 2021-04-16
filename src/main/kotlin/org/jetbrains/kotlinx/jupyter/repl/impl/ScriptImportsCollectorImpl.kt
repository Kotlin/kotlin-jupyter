package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlinx.jupyter.compiler.ScriptImportsCollector
import kotlin.script.experimental.api.SourceCode

class ScriptImportsCollectorImpl : ScriptImportsCollector {
    private val addedImports = mutableListOf<String>()

    override fun collect(source: SourceCode) {
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
