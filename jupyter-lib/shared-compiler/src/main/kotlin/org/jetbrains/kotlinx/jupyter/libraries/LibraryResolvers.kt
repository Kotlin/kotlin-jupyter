package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryReference
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionInfo
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable
import org.jetbrains.kotlinx.jupyter.common.HttpClient
import org.jetbrains.kotlinx.jupyter.common.LibraryDescriptorsManager
import org.jetbrains.kotlinx.jupyter.common.getHttp
import org.jetbrains.kotlinx.jupyter.exceptions.ReplLibraryLoadingException
import org.jetbrains.kotlinx.jupyter.streams.KernelStreams
import java.io.File
import java.io.IOException
import kotlin.reflect.KClass
import kotlin.reflect.cast

class DefaultInfoLibraryResolver(
    private val parent: LibraryResolver,
    private val infoProvider: ResolutionInfoProvider,
    libraryDescriptorsManager: LibraryDescriptorsManager,
    paths: List<File> = emptyList(),
) : LibraryResolver {
    private val resolutionInfos = paths.map { AbstractLibraryResolutionInfo.ByDir(it) }
    private val byDirResolver = getByDirResolver(libraryDescriptorsManager)

    override fun resolve(
        reference: LibraryReference,
        arguments: List<Variable>,
    ): LibraryDefinition? {
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

    private fun transformReference(
        referenceInfo: AbstractLibraryResolutionInfo.Default,
        referenceName: String?,
    ): LibraryReference {
        val transformedInfo = infoProvider.get(referenceInfo.string)
        return LibraryReference(transformedInfo, referenceName)
    }
}

open class ResourcesLibraryResolver(
    parent: LibraryResolver?,
    private val libraryDescriptorsManager: LibraryDescriptorsManager,
    private val classLoader: ClassLoader,
) : ChainedLibraryResolver(parent) {
    override fun tryResolve(
        reference: LibraryReference,
        arguments: List<Variable>,
    ): LibraryDefinition? {
        val text = resolveDescriptorFromResources(reference.name) ?: return null
        return parseLibraryDescriptor(text).convertToDefinition(arguments)
    }

    fun resolveDescriptorFromResources(libraryName: String?): String? {
        libraryName ?: return null
        val url = classLoader.getResource(libraryDescriptorsManager.resourceLibraryPath(libraryName)) ?: return null
        return url.readText()
    }

    fun resolveDescriptorOptionsFromResources(): LibraryDescriptorGlobalOptions {
        val url = classLoader.getResource(libraryDescriptorsManager.resourceOptionsPath()) ?: return DefaultLibraryDescriptorGlobalOptions
        return parseLibraryDescriptorGlobalOptions(url.readText())
    }

    override fun shouldResolve(reference: LibraryReference): Boolean =
        when (reference.info) {
            is AbstractLibraryResolutionInfo.Default -> true
            is AbstractLibraryResolutionInfo.ByClasspath -> true
            else -> false
        }
}

private fun LibraryDescriptorsManager.getDescriptorOptions(ref: String): LibraryDescriptorGlobalOptions {
    val optionsText = downloadGlobalDescriptorOptions(ref) ?: return DefaultLibraryDescriptorGlobalOptions
    return parseLibraryDescriptorGlobalOptions(optionsText)
}

class FallbackLibraryResolver(
    private val httpClient: HttpClient,
    private val libraryDescriptorsManager: LibraryDescriptorsManager,
) : ChainedLibraryResolver() {
    private val resourcesResolver =
        ResourcesLibraryResolver(
            null,
            libraryDescriptorsManager,
            ResourcesLibraryResolver::class.java.classLoader,
        )

    private val standardResolvers =
        listOf(
            getByDirResolver(libraryDescriptorsManager),
            resolverWithOptions<AbstractLibraryResolutionInfo.ByGitRefWithClasspathFallback> { name ->
                if (name == null) throw ReplLibraryLoadingException(message = "Reference library resolver needs name to be specified")

                val descriptorText =
                    try {
                        libraryDescriptorsManager.downloadLibraryDescriptor(ref, name)
                    } catch (e: IOException) {
                        KernelStreams.err.println(
                            "WARNING: Can't resolve library $name from the given reference. " +
                                "Using classpath version of this library. Error: $e",
                        )
                        KernelStreams.err.flush()
                        resourcesResolver.resolveDescriptorFromResources(name)
                    }

                descriptorText to libraryDescriptorsManager.getDescriptorOptions(ref)
            },
            resolverWithOptions<AbstractLibraryResolutionInfo.ByGitRef> { name ->
                if (name == null) throw ReplLibraryLoadingException(message = "Reference library resolver needs name to be specified")
                val descriptorText = libraryDescriptorsManager.downloadLibraryDescriptor(ref, name)

                descriptorText to libraryDescriptorsManager.getDescriptorOptions(ref)
            },
            resolver<AbstractLibraryResolutionInfo.ByFile> {
                file.readText()
            },
            resolver<AbstractLibraryResolutionInfo.ByURL> {
                val response = httpClient.getHttp(url.toString())
                response.text
            },
            resolver<ByNothingLibraryResolutionInfo> { "{}" },
            resolverWithOptions<AbstractLibraryResolutionInfo.ByClasspath> { name ->
                resourcesResolver.resolveDescriptorFromResources(name) to resourcesResolver.resolveDescriptorOptionsFromResources()
            },
            resolver<AbstractLibraryResolutionInfo.Default> { null },
        )

    override fun tryResolve(
        reference: LibraryReference,
        arguments: List<Variable>,
    ): LibraryDefinition? = standardResolvers.firstOrNull { it.accepts(reference) }?.resolve(reference, arguments)
}

class SpecificLibraryResolver<T : LibraryResolutionInfo>(
    private val kClass: KClass<T>,
    val resolveRaw: (T, String?) -> Pair<String?, LibraryDescriptorGlobalOptions>,
) : LibraryResolver {
    fun accepts(reference: LibraryReference): Boolean = kClass.isInstance(reference.info)

    override fun resolve(
        reference: LibraryReference,
        arguments: List<Variable>,
    ): LibraryDefinition? {
        if (!accepts(reference)) return null
        val (text, options) = resolveRaw(kClass.cast(reference.info), reference.name)
        text ?: return null
        return parseLibraryDescriptor(text).convertToDefinition(arguments, options)
    }
}

private inline fun <reified T : LibraryResolutionInfo> resolverWithOptions(
    noinline resolverFun: T.(String?) -> Pair<String?, LibraryDescriptorGlobalOptions>,
) = SpecificLibraryResolver(
    T::class,
    resolverFun,
)

private inline fun <reified T : LibraryResolutionInfo> resolver(noinline resolverFun: T.(String?) -> String?) =
    SpecificLibraryResolver(
        T::class,
    ) { info, descriptorText -> resolverFun(info, descriptorText) to DefaultLibraryDescriptorGlobalOptions }

private fun getByDirResolver(
    libraryDescriptorsManager: LibraryDescriptorsManager,
): SpecificLibraryResolver<AbstractLibraryResolutionInfo.ByDir> =
    resolverWithOptions<AbstractLibraryResolutionInfo.ByDir> { name ->
        if (name == null) throw ReplLibraryLoadingException(null, "Directory library resolver needs library name to be specified")

        val jsonFile = librariesDir.resolve(libraryDescriptorsManager.descriptorFileName(name))
        if (jsonFile.exists() && jsonFile.isFile) {
            val descriptorText = jsonFile.readText()

            val optionsFile = librariesDir.resolve(libraryDescriptorsManager.optionsFileName())
            val options =
                if (optionsFile.exists() && optionsFile.isFile) {
                    parseLibraryDescriptorGlobalOptions(optionsFile.readText())
                } else {
                    DefaultLibraryDescriptorGlobalOptions
                }
            descriptorText to options
        } else {
            null to DefaultLibraryDescriptorGlobalOptions
        }
    }
