package org.jetbrains.kotlin.jupyter.libraries

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.jupyter.LibraryDescriptor
import org.jetbrains.kotlin.jupyter.LibraryDescriptorExt
import org.jetbrains.kotlin.jupyter.LocalCacheDir
import org.jetbrains.kotlin.jupyter.LocalSettingsPath
import org.jetbrains.kotlin.jupyter.log
import java.nio.file.Paths

abstract class LibraryResolver(private val parent: LibraryResolver? = null) {
    protected abstract fun tryResolve(reference: LibraryReference): LibraryDescriptor?
    protected abstract fun save(reference: LibraryReference, descriptor: LibraryDescriptor)
    protected open fun shouldResolve(reference: LibraryReference): Boolean = true

    open val cache: Map<LibraryReference, LibraryDescriptor>? = null

    fun resolve(reference: LibraryReference): LibraryDescriptor? {
        val shouldBeResolved = shouldResolve(reference)
        if (shouldBeResolved) {
            val result = tryResolve(reference)
            if (result != null) {
                return result
            }
        }

        val parentResult = parent?.resolve(reference) ?: return null
        if (shouldBeResolved) {
            save(reference, parentResult)
        }

        return parentResult
    }
}

class FallbackLibraryResolver : LibraryResolver() {
    override fun tryResolve(reference: LibraryReference): LibraryDescriptor {
        return reference.resolve()
    }

    override fun save(reference: LibraryReference, descriptor: LibraryDescriptor) {
        // fallback resolver doesn't cache results
    }
}

class LocalLibraryResolver(
    parent: LibraryResolver?,
    mainLibrariesDir: String
) : LibraryResolver(parent) {
    private val pathsToCheck = listOf(
        Paths.get(LocalSettingsPath, LocalCacheDir).toString(),
        LocalSettingsPath,
        mainLibrariesDir
    )

    override fun shouldResolve(reference: LibraryReference): Boolean {
        return reference.shouldBeCachedLocally
    }

    override fun tryResolve(reference: LibraryReference): LibraryDescriptor? {
        val jsons = pathsToCheck.mapNotNull { dir ->
            val file = reference.getFile(dir)
            if (!file.exists()) null
            else { file.readText() }
        }

        if (jsons.size > 1) log.warn("More than one file for library $reference found in local cache directories")

        val json = jsons.firstOrNull() ?: return null
        return parseLibraryDescriptor(json)
    }

    override fun save(reference: LibraryReference, descriptor: LibraryDescriptor) {
        val dir = pathsToCheck.first()
        val file = reference.getFile(dir)
        file.parentFile.mkdirs()

        val format = Json { prettyPrint = true }
        file.writeText(format.encodeToString(descriptor))
    }

    private fun LibraryReference.getFile(dir: String) = Paths.get(dir, this.key + "." + LibraryDescriptorExt).toFile()
}
