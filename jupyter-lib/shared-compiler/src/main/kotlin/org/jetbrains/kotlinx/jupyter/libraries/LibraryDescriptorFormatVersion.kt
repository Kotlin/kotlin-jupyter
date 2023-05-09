package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplLibraryLoadingException
import org.jetbrains.kotlinx.jupyter.util.StringValueSerializer

@Serializable(LibraryDescriptorFormatVersionSerializer::class)
class LibraryDescriptorFormatVersion(
    val major: Int,
    val minor: Int,
) {
    override fun toString(): String {
        return "$major.$minor"
    }

    companion object {
        fun fromString(str: String): LibraryDescriptorFormatVersion? {
            val components = str.split(".")
            if (components.size != 2) return null
            val major = components[0].toIntOrNull() ?: return null
            val minor = components[1].toIntOrNull() ?: return null
            return LibraryDescriptorFormatVersion(major, minor)
        }
    }
}

object LibraryDescriptorFormatVersionSerializer : StringValueSerializer<LibraryDescriptorFormatVersion>(
    LibraryDescriptorFormatVersion::class,
    { it.toString() },
    { str -> LibraryDescriptorFormatVersion.fromString(str) ?: throw SerializationException("Wrong format of library descriptor format version: $str") },
)

/**
 * 3.1: Initial version
 * 3.2: Changed <required> to <ignored> in libraries properties.
 *      Technically it's a breaking change, but impact is close to zero, so only minor version was increased.
 */
val currentLibraryDescriptorFormatVersion = LibraryDescriptorFormatVersion(3, 2)

fun isDescriptorFormatVersionCompatible(formatVersion: LibraryDescriptorFormatVersion?): Boolean {
    if (formatVersion == null) return true
    if (formatVersion.major != currentLibraryDescriptorFormatVersion.major) return false
    return formatVersion.minor <= currentLibraryDescriptorFormatVersion.minor
}

fun assertDescriptorFormatVersionCompatible(descriptor: LibraryDescriptor) {
    if (!isDescriptorFormatVersionCompatible(descriptor.formatVersion)) {
        throw ReplLibraryLoadingException(
            libraryName = descriptor.description?.let { "<$it>" },
            message = "This descriptor format version is not supported: " +
                "descriptor format version is ${descriptor.formatVersion}, " +
                "kernel format version is $currentLibraryDescriptorFormatVersion",
        )
    }
}
