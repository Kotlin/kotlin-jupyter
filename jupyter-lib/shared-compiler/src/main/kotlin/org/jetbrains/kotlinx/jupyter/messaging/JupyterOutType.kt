package org.jetbrains.kotlinx.jupyter.messaging

enum class JupyterOutType {
    STDOUT, STDERR;
    fun optionName() = name.lowercase()
}
