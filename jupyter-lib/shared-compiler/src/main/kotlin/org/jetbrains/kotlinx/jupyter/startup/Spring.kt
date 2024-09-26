package org.jetbrains.kotlinx.jupyter.startup

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType

val defaultSpringAppPorts =
    createKernelPorts { socketType ->
        when (socketType) {
            JupyterSocketType.HB -> 50501
            JupyterSocketType.SHELL -> 50502
            JupyterSocketType.CONTROL -> 50503
            JupyterSocketType.STDIN -> 50504
            JupyterSocketType.IOPUB -> 50505
        }
    }

const val DEFAULT_SPRING_SIGNATURE_KEY = "xxx"
