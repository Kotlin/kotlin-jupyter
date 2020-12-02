package org.jetbrains.kotlin.jupyter.libraries

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.jetbrains.kotlin.jupyter.compiler.util.ReplCompilerException
import org.jetbrains.kotlin.jupyter.config.getLogger

fun parseLibraryDescriptor(json: String): LibraryDescriptor {
    val res = Json.parseToJsonElement(json)
    if (res is JsonObject) return parseLibraryDescriptor(res)

    throw ReplCompilerException("Result of library descriptor parsing is of type ${res.javaClass.canonicalName} which is unexpected")
}

fun parseLibraryDescriptor(json: JsonObject): LibraryDescriptor {
    return Json.decodeFromJsonElement(json)
}

fun parseLibraryDescriptors(libJsons: Map<String, JsonObject>): Map<String, LibraryDescriptor> {
    val logger = getLogger()
    return libJsons.mapValues {
        logger.info("Parsing '${it.key}' descriptor")
        parseLibraryDescriptor(it.value)
    }
}
