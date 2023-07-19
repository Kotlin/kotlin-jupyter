package org.jetbrains.kotlinx.jupyter.protocol

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType

interface JupyterSocketManagerBase {
    fun fromSocketType(type: JupyterSocketType): JupyterSocket
}
