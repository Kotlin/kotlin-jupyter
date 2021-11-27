package org.jetbrains.kotlinx.jupyter.testkit.notebook

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class JupyterNotebook(
    val cells: List<JupyterCell>,
    val metadata: JsonObject? = null,
)
