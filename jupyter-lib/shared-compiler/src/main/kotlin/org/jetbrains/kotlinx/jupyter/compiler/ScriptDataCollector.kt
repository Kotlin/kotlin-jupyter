package org.jetbrains.kotlinx.jupyter.compiler

import kotlin.script.experimental.api.SourceCode

interface ScriptDataCollector {
    fun collect(scriptInfo: ScriptInfo)

    class ScriptInfo(val source: SourceCode, val isUserScript: Boolean)
}
