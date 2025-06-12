package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.session.JupyterSession
import org.jetbrains.kotlinx.jupyter.api.session.JupyterSessionProvider

class JupyterSessionProviderImpl : JupyterSessionProvider {
    private val instance = JupyterSessionImpl()

    override fun getCurrentSession(): JupyterSession = instance

    private class JupyterSessionImpl : JupyterSession
}
