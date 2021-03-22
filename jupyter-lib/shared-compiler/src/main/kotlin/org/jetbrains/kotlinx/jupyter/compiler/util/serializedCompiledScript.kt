package org.jetbrains.kotlinx.jupyter.compiler.util

import kotlinx.serialization.Serializable

@Serializable
data class SerializedCompiledScript(
    val fileName: String,
    val data: String,
)

@Serializable
data class SerializedCompiledScriptsData(
    val scripts: List<SerializedCompiledScript>
)
