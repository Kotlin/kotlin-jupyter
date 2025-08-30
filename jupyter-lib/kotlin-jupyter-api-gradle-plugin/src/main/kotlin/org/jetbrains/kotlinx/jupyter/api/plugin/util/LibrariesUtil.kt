package org.jetbrains.kotlinx.jupyter.api.plugin.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

data class FQNAware(
    val fqn: String,
)

class LibrariesScanResult(
    val definitions: Set<FQNAware> = emptySet(),
    val producers: Set<FQNAware> = emptySet(),
    val descriptors: Set<String> = emptySet(),
)

val emptyScanResult = LibrariesScanResult()

internal object LibrariesScanResultSerializer : JsonSerializer<LibrariesScanResult> {
    override fun serialize(
        src: LibrariesScanResult,
        typeOfSrc: Type,
        context: JsonSerializationContext,
    ): JsonElement =
        JsonObject().apply {
            add("definitions", context.serialize(src.definitions))
            add("producers", context.serialize(src.producers))
            if (src.descriptors.isNotEmpty()) {
                val descriptorsArray =
                    JsonArray().apply {
                        for (descriptor in src.descriptors) {
                            try {
                                add(JsonParser.parseString(descriptor))
                            } catch (e: Throwable) {
                                throw IllegalArgumentException("Failed to parse descriptor as JSON: $descriptor", e)
                            }
                        }
                    }

                add("descriptors", descriptorsArray)
            }
        }
}
