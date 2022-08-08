package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryReference
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionInfo
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable
import org.jetbrains.kotlinx.jupyter.common.getHttp
import org.jetbrains.kotlinx.jupyter.common.text
import org.jetbrains.kotlinx.jupyter.config.getLogger
import org.jetbrains.kotlinx.jupyter.exceptions.ReplLibraryLoadingException
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.cast

class DefaultInfoLibraryResolver(
    private val parent: LibraryResolver,
    private val infoProvider: ResolutionInfoProvider,
    paths: List<File> = emptyList(),
) : LibraryResolver {
    private val resolutionInfos = paths.map { AbstractLibraryResolutionInfo.ByDir(it) }

    override fun resolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition? {
        val referenceInfo = reference.info
        if (referenceInfo !is AbstractLibraryResolutionInfo.Default) return parent.resolve(reference, arguments)

        val definitionText = resolutionInfos.asSequence().mapNotNull { byDirResolver.resolveRaw(it, reference.name) }.firstOrNull()
        if (definitionText != null) return parseLibraryDescriptor(definitionText).convertToDefinition(arguments)

        val newReference = transformReference(referenceInfo, reference.name)
        return parent.resolve(newReference, arguments)
    }

    private fun transformReference(referenceInfo: AbstractLibraryResolutionInfo.Default, referenceName: String?): LibraryReference {
        val transformedInfo = infoProvider.get(referenceInfo.string)
        return LibraryReference(transformedInfo, referenceName)
    }
}

class LocalLibraryResolver(
    parent: LibraryResolver?,
    mainLibrariesDir: File?
) : ChainedLibraryResolver(parent) {
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

open class ResourcesLibraryResolver(
    parent: LibraryResolver?,
    private val classLoader: ClassLoader,
) : ChainedLibraryResolver(parent) {
    override fun tryResolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition? {
        val name = reference.name ?: return null
        val url = classLoader.getResource(KERNEL_LIBRARIES.resourceFilePath(name)) ?: return null
        val descriptorText = url.readText()
        return parseLibraryDescriptor(descriptorText).convertToDefinition(arguments)
    }
}

object FallbackLibraryResolver : ChainedLibraryResolver() {
    private val standardResolvers = listOf(
        byDirResolver,
        resolver<AbstractLibraryResolutionInfo.ByGitRef> { name ->
            if (name == null) throw ReplLibraryLoadingException(message = "Reference library resolver needs name to be specified")
            KERNEL_LIBRARIES.downloadLibraryDescriptor(sha, name)
        },
        resolver<AbstractLibraryResolutionInfo.ByFile> {
            file.readText()
        },
        resolver<AbstractLibraryResolutionInfo.ByURL> {
            val response = getHttp(url.toString())
            response.text
        },
        resolver<ByNothingLibraryResolutionInfo> { "{}" },
        resolver<AbstractLibraryResolutionInfo.Default> { null }
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

private val byDirResolver = resolver<AbstractLibraryResolutionInfo.ByDir> { name ->
    if (name == null) throw ReplLibraryLoadingException(name, "Directory library resolver needs library name to be specified")

    val jsonFile = librariesDir.resolve(KERNEL_LIBRARIES.descriptorFileName(name))
    if (jsonFile.exists() && jsonFile.isFile) jsonFile.readText()
    else null
}
