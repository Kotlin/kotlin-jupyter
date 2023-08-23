package org.jetbrains.kotlinx.jupyter.compiler

import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo

interface ScriptDeclarationsProvider {
    fun getLastSnippetDeclarations(): List<DeclarationInfo>
}

interface ScriptDeclarationsCollectorInternal : ScriptDeclarationsProvider, ScriptDataCollector
