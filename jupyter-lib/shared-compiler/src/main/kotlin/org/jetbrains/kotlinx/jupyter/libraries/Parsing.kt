package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory

private val jsonParser = Json { ignoreUnknownKeys = true }

fun parseLibraryDescriptor(json: String): LibraryDescriptor =
    try {
        jsonParser.decodeFromString(json)
    } catch (e: SerializationException) {
        throw ReplException("Error during library deserialization. Library descriptor text:\n$json", e)
    }

fun parseLibraryDescriptor(jsonObject: JsonObject): LibraryDescriptor =
    try {
        jsonParser.decodeFromJsonElement(jsonObject)
    } catch (e: SerializationException) {
        throw ReplException("Error during library deserialization. Library descriptor text:\n$jsonObject", e)
    }

fun parseLibraryDescriptors(
    loggerFactory: KernelLoggerFactory,
    libJsons: Map<String, String>,
): Map<String, LibraryDescriptor> {
    val logger = loggerFactory.getLogger(LibraryDescriptor::class.java)
    return libJsons.mapValues {
        logger.info("Parsing '${it.key}' descriptor")
        parseLibraryDescriptor(it.value)
    }
}
