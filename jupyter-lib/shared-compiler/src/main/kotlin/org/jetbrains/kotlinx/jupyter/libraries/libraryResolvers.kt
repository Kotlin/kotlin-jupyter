package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.compiler.util.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.config.getLogger
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.cast

interface LibraryResolver {
    fun resolve(reference: LibraryReference): LibraryDefinition?
}

abstract class LibraryDescriptorResolver(private val parent: LibraryResolver? = null) : LibraryResolver {
    protected abstract fun tryResolve(reference: LibraryReference): LibraryDefinition?
    protected open fun save(reference: LibraryReference, definition: LibraryDefinition) {}
    protected open fun shouldResolve(reference: LibraryReference): Boolean = true

    override fun resolve(reference: LibraryReference): LibraryDefinition? {
        val shouldBeResolved = shouldResolve(reference)
        if (shouldBeResolved) {
            val result = tryResolve(reference)
            if (result != null) return result
        }

        val parentResult = parent?.resolve(reference) ?: return null
        if (shouldBeResolved) {
            save(reference, parentResult)
        }

        return parentResult
    }
}

class DefaultInfoLibraryResolver(
    private val parent: LibraryResolver,
    private val infoProvider: ResolutionInfoProvider,
    paths: List<Path> = emptyList(),
) : LibraryResolver {
    private val resolutionInfos = paths.map { LibraryResolutionInfo.ByDir(it.toFile()) }

    override fun resolve(reference: LibraryReference): LibraryDefinition? {
        val referenceInfo = reference.info
        if (referenceInfo !is LibraryResolutionInfo.Default) return parent.resolve(reference)

        val definitionText = resolutionInfos.asSequence().mapNotNull { byDirResolver.resolveRaw(it, reference.name) }.firstOrNull()
        if (definitionText != null) return parseLibraryDescriptor(definitionText)

        val newReference = transformReference(referenceInfo, reference.name)
        return parent.resolve(newReference)
    }

    private fun transformReference(referenceInfo: LibraryResolutionInfo.Default, referenceName: String?): LibraryReference {
        val transformedInfo = infoProvider.get(referenceInfo.string)
        return LibraryReference(transformedInfo, referenceName)
    }
}

class LocalLibraryResolver(
    parent: LibraryResolver?,
    mainLibrariesDir: Path?
) : LibraryDescriptorResolver(parent) {
    private val logger = getLogger()
    private val pathsToCheck: List<Path>

    init {
        val paths = mutableListOf(
            LocalSettingsPath.resolve(LocalCacheDir),
            LocalSettingsPath.resolve(LibrariesDir)
        )
        mainLibrariesDir?.let { paths.add(it) }

        pathsToCheck = paths
    }

    override fun shouldResolve(reference: LibraryReference): Boolean {
        return reference.shouldBeCachedLocally
    }

    override fun tryResolve(reference: LibraryReference): LibraryDescriptor? {
        val files = pathsToCheck.mapNotNull { dir ->
            val file = reference.getFile(dir)
            if (file.exists()) file else null
        }

        if (files.size > 1) {
            logger.warn("More than one file for library $reference found in local cache directories")
        }

        val jsonFile = files.firstOrNull() ?: return null
        return parseLibraryDescriptor(jsonFile.readText())
    }

    override fun save(reference: LibraryReference, definition: LibraryDefinition) {
        if (definition !is LibraryDescriptor) return
        val dir = pathsToCheck.first()
        val file = reference.getFile(dir)
        file.parentFile.mkdirs()

        val format = Json { prettyPrint = true }
        file.writeText(format.encodeToString(definition))
    }

    private fun LibraryReference.getFile(dir: Path) = dir.resolve("$key.$LibraryDescriptorExt").toFile()
}

object FallbackLibraryResolver : LibraryDescriptorResolver() {
    private val standardResolvers = listOf(
        byDirResolver,
        resolver<LibraryResolutionInfo.ByGitRef> { name ->
            if (name == null) throw ReplCompilerException("Reference library resolver needs name to be specified")

            val url = "$GitHubApiPrefix/contents/$LibrariesDir/$name.$LibraryDescriptorExt?ref=$sha"
            getLogger().info("Requesting library descriptor at $url")
            val response = getHttp(url).jsonObject

            val downloadURL = response["download_url"].toString()
            val res = getHttp(downloadURL)
            res.text
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

    override fun tryResolve(reference: LibraryReference): LibraryDefinition? {
        return standardResolvers.firstOrNull { it.accepts(reference) }?.resolve(reference)
    }
}

class SpecificLibraryResolver<T : LibraryResolutionInfo>(private val kClass: KClass<T>, val resolveRaw: (T, String?) -> String?) : LibraryResolver {
    fun accepts(reference: LibraryReference): Boolean {
        return reference.info::class == kClass
    }

    override fun resolve(reference: LibraryReference): LibraryDefinition? {
        if (!accepts(reference)) return null
        val text = resolveRaw(kClass.cast(reference.info), reference.name) ?: return null
        return parseLibraryDescriptor(text)
    }
}

private inline fun <reified T : LibraryResolutionInfo> resolver(noinline resolverFun: T.(String?) -> String?) = SpecificLibraryResolver(T::class, resolverFun)

private val byDirResolver = resolver<LibraryResolutionInfo.ByDir> { name ->
    if (name == null) throw ReplCompilerException("Directory library resolver needs library name to be specified")

    val jsonFile = librariesDir.resolve("$name.$LibraryDescriptorExt")
    if (jsonFile.exists() && jsonFile.isFile) jsonFile.readText()
    else null
}
