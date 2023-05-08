package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryReference
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionInfo
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable
import org.jetbrains.kotlinx.jupyter.common.getHttp
import org.jetbrains.kotlinx.jupyter.common.text
import org.jetbrains.kotlinx.jupyter.config.KernelStreams
import org.jetbrains.kotlinx.jupyter.config.getLogger
import org.jetbrains.kotlinx.jupyter.exceptions.ReplLibraryLoadingException
import java.io.File
import java.io.IOException
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

        val result = resolutionInfos.asSequence().map { byDirResolver.resolveRaw(it, reference.name) }.firstOrNull()
        if (result != null) {
            val (definitionText, options) = result
            if (definitionText != null) return parseLibraryDescriptor(definitionText).convertToDefinition(arguments, options)
        }

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
    mainLibrariesDir: File?,
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
        val text = resolveDescriptorFromResources(reference.name) ?: return null
        return parseLibraryDescriptor(text).convertToDefinition(arguments)
    }

    fun resolveDescriptorFromResources(libraryName: String?): String? {
        libraryName ?: return null
        val url = classLoader.getResource(KERNEL_LIBRARIES.resourceLibraryPath(libraryName)) ?: return null
        return url.readText()
    }

    fun resolveDescriptorOptionsFromResources(): LibraryDescriptorGlobalOptions {
        val url = classLoader.getResource(KERNEL_LIBRARIES.resourceOptionsPath()) ?: return DefaultLibraryDescriptorGlobalOptions
        return parseLibraryDescriptorGlobalOptions(url.readText())
    }

    override fun shouldResolve(reference: LibraryReference): Boolean {
        return when (reference.info) {
            is AbstractLibraryResolutionInfo.Default -> true
            is AbstractLibraryResolutionInfo.ByClasspath -> true
            else -> false
        }
    }
}

private fun getDescriptorOptions(sha: String): LibraryDescriptorGlobalOptions {
    val optionsText = KERNEL_LIBRARIES.downloadGlobalDescriptorOptions(sha) ?: return DefaultLibraryDescriptorGlobalOptions
    return parseLibraryDescriptorGlobalOptions(optionsText)
}

object FallbackLibraryResolver : ChainedLibraryResolver() {
    private val standardResolvers = listOf(
        byDirResolver,
        resolverWithOptions<AbstractLibraryResolutionInfo.ByGitRefWithClasspathFallback> { name ->
            if (name == null) throw ReplLibraryLoadingException(message = "Reference library resolver needs name to be specified")

            val descriptorText = try {
                KERNEL_LIBRARIES.downloadLibraryDescriptor(sha, name)
            } catch (e: IOException) {
                KernelStreams.err.println(
                    "WARNING: Can't resolve library $name from the given reference. Using classpath version of this library. Error: $e",
                )
                KernelStreams.err.flush()
                resourcesResolver.resolveDescriptorFromResources(name)
            }

            descriptorText to getDescriptorOptions(sha)
        },
        resolverWithOptions<AbstractLibraryResolutionInfo.ByGitRef> { name ->
            if (name == null) throw ReplLibraryLoadingException(message = "Reference library resolver needs name to be specified")
            val descriptorText = KERNEL_LIBRARIES.downloadLibraryDescriptor(sha, name)

            descriptorText to getDescriptorOptions(sha)
        },
        resolver<AbstractLibraryResolutionInfo.ByFile> {
            file.readText()
        },
        resolver<AbstractLibraryResolutionInfo.ByURL> {
            val response = getHttp(url.toString())
            response.text
        },
        resolver<ByNothingLibraryResolutionInfo> { "{}" },
        resolverWithOptions<AbstractLibraryResolutionInfo.ByClasspath> { name ->
            resourcesResolver.resolveDescriptorFromResources(name) to resourcesResolver.resolveDescriptorOptionsFromResources()
        },
        resolver<AbstractLibraryResolutionInfo.Default> { null },
    )

    override fun tryResolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition? {
        return standardResolvers.firstOrNull { it.accepts(reference) }?.resolve(reference, arguments)
    }
}

class SpecificLibraryResolver<T : LibraryResolutionInfo>(
    private val kClass: KClass<T>,
    val resolveRaw: (T, String?) -> Pair<String?, LibraryDescriptorGlobalOptions>,
) : LibraryResolver {
    fun accepts(reference: LibraryReference): Boolean {
        return kClass.isInstance(reference.info)
    }

    override fun resolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition? {
        if (!accepts(reference)) return null
        val (text, options) = resolveRaw(kClass.cast(reference.info), reference.name)
        text ?: return null
        return parseLibraryDescriptor(text).convertToDefinition(arguments, options)
    }
}

private inline fun <reified T : LibraryResolutionInfo> resolverWithOptions(noinline resolverFun: T.(String?) -> Pair<String?, LibraryDescriptorGlobalOptions>) = SpecificLibraryResolver(
    T::class,
    resolverFun,
)

private inline fun <reified T : LibraryResolutionInfo> resolver(noinline resolverFun: T.(String?) -> String?) = SpecificLibraryResolver(
    T::class,
) { info, descriptorText -> resolverFun(info, descriptorText) to DefaultLibraryDescriptorGlobalOptions }

private val byDirResolver = resolverWithOptions<AbstractLibraryResolutionInfo.ByDir> { name ->
    if (name == null) throw ReplLibraryLoadingException(null, "Directory library resolver needs library name to be specified")

    val jsonFile = librariesDir.resolve(KERNEL_LIBRARIES.descriptorFileName(name))
    if (jsonFile.exists() && jsonFile.isFile) {
        val descriptorText = jsonFile.readText()

        val optionsFile = librariesDir.resolve(KERNEL_LIBRARIES.optionsFileName())
        val options = if (optionsFile.exists() && optionsFile.isFile) {
            parseLibraryDescriptorGlobalOptions(optionsFile.readText())
        } else DefaultLibraryDescriptorGlobalOptions
        descriptorText to options
    } else null to DefaultLibraryDescriptorGlobalOptions
}

private val resourcesResolver = ResourcesLibraryResolver(null, ResourcesLibraryResolver::class.java.classLoader)
