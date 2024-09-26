package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException

fun parseLibraryDescriptor(json: String): LibraryDescriptor {
    return try {
        Json.decodeFromString(json)
    } catch (e: SerializationException) {
        throw ReplException("Error during library deserialization. Library descriptor text:\n$json", e)
    }
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
