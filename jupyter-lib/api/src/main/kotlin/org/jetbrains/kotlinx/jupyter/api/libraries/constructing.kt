package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.Notebook

fun createLibrary(notebook: Notebook, builder: JupyterIntegration.Builder.() -> Unit): LibraryDefinition {
    val o = object : JupyterIntegration() {
        override fun Builder.onLoaded() {
            builder()
        }
    }
    return o.getDefinitions(notebook).single()
}
