package org.jetbrains.kotlinx.jupyter.protocol.api

import java.util.Locale

/**
 * Jupyter connection socket types.
 * You can find information about each Jupyter socket type here:
 * https://jupyter-client.readthedocs.io/en/stable/messaging.html#introduction
 *
 * For now, only adding callbacks for messages on `control` and `shell` sockets makes sense.
 */
enum class JupyterSocketType {
    HB,
    SHELL,
    CONTROL,
    STDIN,
    IOPUB,
}

/** This name is used in Jupyter messaging protocol (as channel identifier) */
val JupyterSocketType.jupyterName get() = name.lowercase(Locale.getDefault())
