package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplException

@Serializable
@Suppress("CanBeParameter")
class LibraryDescriptorGlobalOptionsImpl(
    private val ignoredPropertyPatterns: List<String>?,
) : LibraryDescriptorGlobalOptions {
    @Transient
    private val ignoredPropertyRegExps = ignoredPropertyPatterns.orEmpty().map { Regex(it) }

    override fun isPropertyIgnored(propertyName: String): Boolean = ignoredPropertyRegExps.any { it.matches(propertyName) }
}

fun parseLibraryDescriptorGlobalOptions(json: String): LibraryDescriptorGlobalOptions =
    try {
        Json.decodeFromString<LibraryDescriptorGlobalOptionsImpl>(json)
    } catch (e: SerializationException) {
        throw ReplException("Error during descriptor global options deserialization. Options text:\n$json", e)
    }
