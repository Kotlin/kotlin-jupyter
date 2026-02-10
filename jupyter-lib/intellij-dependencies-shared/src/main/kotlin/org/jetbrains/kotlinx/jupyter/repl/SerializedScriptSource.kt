package org.jetbrains.kotlinx.jupyter.repl

import kotlinx.serialization.Serializable

@Serializable
data class SerializedScriptSource(
    val fileName: String,
    val text: String,
)
