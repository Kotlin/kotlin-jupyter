package org.jetbrains.kotlinx.jupyter.api.session

interface JupyterSessionProvider {
    fun getCurrentSession(): JupyterSession
}
