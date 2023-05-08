package org.jetbrains.kotlinx.jupyter.libraries

import io.github.xn32.json5k.Json5
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException

@Serializable
@Suppress("CanBeParameter")
class LibraryDescriptorGlobalOptionsImpl(
    private val ignoredPropertyPatterns: List<String>?,
) : LibraryDescriptorGlobalOptions {
    @Transient
    private val ignoredPropertyRegExps = ignoredPropertyPatterns.orEmpty().map { Regex(it) }

    override fun isPropertyIgnored(propertyName: String): Boolean {
        return ignoredPropertyRegExps.any { it.matches(propertyName) }
    }
}

fun parseLibraryDescriptorGlobalOptions(json: String): LibraryDescriptorGlobalOptions {
    return try {
        Json5.decodeFromString<LibraryDescriptorGlobalOptionsImpl>(json)
    } catch (e: SerializationException) {
        throw ReplException("Error during descriptor global options deserialization. Options text:\n$json", e)
    }
}
