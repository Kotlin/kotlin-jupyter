package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.common.getHttp
import org.jetbrains.kotlinx.jupyter.common.text
import org.jetbrains.kotlinx.jupyter.config.getLogger
import org.jetbrains.kotlinx.jupyter.exceptions.ReplLibraryLoadingException
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.cast

interface LibraryResolver {
    fun resolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition?
}

abstract class LibraryDescriptorResolver(private val parent: LibraryResolver? = null) : LibraryResolver {
    protected abstract fun tryResolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition?
    protected open fun save(reference: LibraryReference, definition: LibraryDefinition) {}
    protected open fun shouldResolve(reference: LibraryReference): Boolean = true

    override fun resolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition? {
        val shouldBeResolved = shouldResolve(reference)
        if (shouldBeResolved) {
            val result = tryResolve(reference, arguments)
            if (result != null) return result
        }

        val parentResult = parent?.resolve(reference, arguments) ?: return null
        if (shouldBeResolved) {
            save(reference, parentResult)
        }

        return parentResult
    }
}

class DefaultInfoLibraryResolver(
    private val parent: LibraryResolver,
    private val infoProvider: ResolutionInfoProvider,
    paths: List<File> = emptyList(),
) : LibraryResolver {
    private val resolutionInfos = paths.map { LibraryResolutionInfo.ByDir(it) }

    override fun resolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition? {
        val referenceInfo = reference.info
        if (referenceInfo !is LibraryResolutionInfo.Default) return parent.resolve(reference, arguments)

        val definitionText = resolutionInfos.asSequence().mapNotNull { byDirResolver.resolveRaw(it, reference.name) }.firstOrNull()
        if (definitionText != null) return parseLibraryDescriptor(definitionText).convertToDefinition(arguments)

        val newReference = transformReference(referenceInfo, reference.name)
        return parent.resolve(newReference, arguments)
    }

    private fun transformReference(referenceInfo: LibraryResolutionInfo.Default, referenceName: String?): LibraryReference {
        val transformedInfo = infoProvider.get(referenceInfo.string)
        return LibraryReference(transformedInfo, referenceName)
    }
}

class LocalLibraryResolver(
    parent: LibraryResolver?,
    mainLibrariesDir: File?
) : LibraryDescriptorResolver(parent) {
    private val logger = getLogger()
    private val pathsToCheck: List<File>

    init {
        val paths = mutableListOf(
            KERNEL_LIBRARIES.userCacheDir,
            KERNEL_LIBRARIES.userLibrariesDir,
        )
        mainLibrariesDir?.let { paths.add(it) }

        pathsToCheck = paths
    }

    override fun shouldResolve(reference: LibraryReference): Boolean {
        return reference.shouldBeCachedLocally
    }

    override fun tryResolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition? {
        val files = pathsToCheck.mapNotNull { dir ->
            val file = reference.getFile(dir)
            if (file.exists()) file else null
        }

        if (files.size > 1) {
            logger.warn("More than one file for library $reference found in local cache directories")
        }

        val jsonFile = files.firstOrNull() ?: return null
        return parseLibraryDescriptor(jsonFile.readText()).convertToDefinition(arguments)
    }

    override fun save(reference: LibraryReference, definition: LibraryDefinition) {
        val text = definition.originalDescriptorText ?: return
        val dir = pathsToCheck.first()
        val file = reference.getFile(dir)
        file.parentFile.mkdirs()
        file.writeText(text)
    }

    private fun LibraryReference.getFile(dir: File) = dir.resolve(KERNEL_LIBRARIES.descriptorFileName(key))
}

object FallbackLibraryResolver : LibraryDescriptorResolver() {
    private val standardResolvers = listOf(
        byDirResolver,
        resolver<LibraryResolutionInfo.ByGitRef> { name ->
            if (name == null) throw ReplLibraryLoadingException(message = "Reference library resolver needs name to be specified")
            KERNEL_LIBRARIES.downloadLibraryDescriptor(sha, name)
        },
        resolver<LibraryResolutionInfo.ByFile> {
            file.readText()
        },
        resolver<LibraryResolutionInfo.ByURL> {
            val response = getHttp(url.toString())
            response.text
        },
        resolver<LibraryResolutionInfo.ByNothing> { "{}" },
        resolver<LibraryResolutionInfo.Default> { null }
    )

    override fun tryResolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition? {
        return standardResolvers.firstOrNull { it.accepts(reference) }?.resolve(reference, arguments)
    }
}

class SpecificLibraryResolver<T : LibraryResolutionInfo>(private val kClass: KClass<T>, val resolveRaw: (T, String?) -> String?) : LibraryResolver {
    fun accepts(reference: LibraryReference): Boolean {
        return kClass.isInstance(reference.info)
    }

    override fun resolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition? {
        if (!accepts(reference)) return null
        val text = resolveRaw(kClass.cast(reference.info), reference.name) ?: return null
        return parseLibraryDescriptor(text).convertToDefinition(arguments)
    }
}

private inline fun <reified T : LibraryResolutionInfo> resolver(noinline resolverFun: T.(String?) -> String?) = SpecificLibraryResolver(T::class, resolverFun)

private val byDirResolver = resolver<LibraryResolutionInfo.ByDir> { name ->
    if (name == null) throw ReplLibraryLoadingException(name, "Directory library resolver needs library name to be specified")

    val jsonFile = librariesDir.resolve(KERNEL_LIBRARIES.descriptorFileName(name))
    if (jsonFile.exists() && jsonFile.isFile) jsonFile.readText()
    else null
}
