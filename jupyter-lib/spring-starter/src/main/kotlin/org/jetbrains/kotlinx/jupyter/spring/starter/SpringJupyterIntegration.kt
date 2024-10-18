package org.jetbrains.kotlinx.jupyter.spring.starter

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration

@Suppress("unused")
class SpringJupyterIntegration : JupyterIntegration() {
    override fun Builder.onLoaded() {
        declareAllBeansInLibrary()
    }
}
