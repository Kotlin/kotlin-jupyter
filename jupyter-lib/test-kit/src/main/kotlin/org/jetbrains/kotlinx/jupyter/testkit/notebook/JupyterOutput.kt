package org.jetbrains.kotlinx.jupyter.testkit.notebook

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class JupyterOutput(
    val data: JsonObject? = null,
    val execution_count: Int? = null,
    val metadata: JsonObject? = null,
    val output_type: String,
)
