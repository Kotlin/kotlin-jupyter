package org.jetbrains.kotlinx.jupyter.testkit.notebook

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File

object JupyterNotebookParser {
    private val parser = Json { ignoreUnknownKeys = true }

    fun parse(text: String): JupyterNotebook {
        return parser.decodeFromString(serializer(), text)
    }

    fun parse(file: File): JupyterNotebook {
        return parse(file.readText())
    }
}
