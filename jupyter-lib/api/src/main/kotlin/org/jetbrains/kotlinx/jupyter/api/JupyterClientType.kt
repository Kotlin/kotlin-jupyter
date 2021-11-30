package org.jetbrains.kotlinx.jupyter.api

/**
 * Jupyter client which is used for running the instance of a kernel
 */
enum class JupyterClientType {
    UNKNOWN,
    KERNEL_TESTS,

    /**
     * [Classic Jupyter Notebook](https://jupyter-notebook.readthedocs.io/en/stable/)
     */
    JUPYTER_NOTEBOOK,

    /**
     * [Jupyter Lab](https://jupyter.org/install.html)
     */
    JUPYTER_LAB,

    /**
     * [JetBrains Datalore](https://blog.jetbrains.com/datalore/)
     */
    DATALORE,

    /**
     * [Kotlin Notebook plugin for IDEA](https://plugins.jetbrains.com/plugin/16340-kotlin-notebook)
     */
    KOTLIN_NOTEBOOK,
}
