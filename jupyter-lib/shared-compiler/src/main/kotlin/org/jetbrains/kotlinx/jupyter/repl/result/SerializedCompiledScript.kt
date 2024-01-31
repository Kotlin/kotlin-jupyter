package org.jetbrains.kotlinx.jupyter.repl.result

import kotlinx.serialization.Serializable

@Serializable
data class SerializedCompiledScript(
    val fileName: String,
    val data: String,
    val isImplicitReceiver: Boolean,
)
