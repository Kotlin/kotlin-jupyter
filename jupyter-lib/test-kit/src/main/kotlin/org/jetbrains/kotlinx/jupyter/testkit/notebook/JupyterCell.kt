package org.jetbrains.kotlinx.jupyter.testkit.notebook

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class JupyterCell(
    val cell_type: String,
    val id: String? = null,
    val metadata: JsonObject? = null,
    val source: List<String> = emptyList(),
    val outputs: List<JupyterOutput> = emptyList(),
)
