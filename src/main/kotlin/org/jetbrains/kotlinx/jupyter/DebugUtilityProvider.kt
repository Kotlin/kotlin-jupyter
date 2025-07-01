package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.Notebook

class DebugUtilityProvider(
    private val notebook: Notebook,
) {
    fun getPresentableVarsState(): Map<String, Result<Any?>> = notebook.variablesState.mapValues { it.value.value }
}
