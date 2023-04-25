package org.jetbrains.kotlinx.jupyter.test.integrations

import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.declare
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration

class MultiConstructor1(
    private val name: String,
) : JupyterIntegration() {
    constructor(notebook: Notebook) : this("lib-1") {
        throw IllegalStateException("should not be called")
    }
    constructor(notebook: Notebook, options: Map<String, String>) : this("lib-2")

    override fun Builder.onLoaded() {
        onLoaded {
            declare("multiConstructor1" to name)
        }
    }
}
