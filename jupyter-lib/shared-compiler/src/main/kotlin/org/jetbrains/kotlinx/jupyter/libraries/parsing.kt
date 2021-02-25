package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.compiler.util.ReplException
import org.jetbrains.kotlinx.jupyter.config.getLogger

fun parseLibraryDescriptor(json: String): LibraryDescriptor {
    return try {
        Json.decodeFromString(json)
    } catch (e: SerializationException) {
        throw ReplException("Error during library deserialization. Library descriptor text:\n$json", e)
    }
}

fun parseLibraryDescriptors(libJsons: Map<String, String>): Map<String, LibraryDescriptor> {
    val logger = getLogger()
    return libJsons.mapValues {
        logger.info("Parsing '${it.key}' descriptor")
        parseLibraryDescriptor(it.value)
    }
}
