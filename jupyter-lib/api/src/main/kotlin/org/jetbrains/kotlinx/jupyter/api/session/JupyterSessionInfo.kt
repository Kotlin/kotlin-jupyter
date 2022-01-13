package org.jetbrains.kotlinx.jupyter.api.session

import java.util.ServiceLoader

object JupyterSessionInfo {
    private val loader = ServiceLoader.load(JupyterSessionProvider::class.java)

    fun isRunWithKernel(): Boolean {
        val provider = loader.findFirst().orElse(null)
        return provider != null
    }
}
