package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionImpl
import org.jetbrains.kotlinx.jupyter.compiler.util.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.config.getLogger
import org.jetbrains.kotlinx.jupyter.util.replaceVariables
import java.nio.file.Paths

interface LibraryResolver {
    fun resolve(reference: LibraryReference, vars: List<Variable>): LibraryDefinition?
}

abstract class LibraryDescriptorResolver(private val parent: LibraryResolver? = null) : LibraryResolver {
    protected abstract fun tryResolve(reference: LibraryReference): LibraryDescriptor?
    protected abstract fun save(reference: LibraryReference, descriptor: LibraryDescriptor)
    protected open fun shouldResolve(reference: LibraryReference): Boolean = true

    open val cache: Map<LibraryReference, LibraryDescriptor>? = null

    override fun resolve(reference: LibraryReference, vars: List<Variable>): LibraryDefinition? {
        val shouldBeResolved = shouldResolve(reference)
        if (shouldBeResolved) {
            val result = tryResolve(reference)
            if (result != null) {
                val mapping = substituteArguments(result.variables, vars)

                return processDescriptor(result, mapping)
            }
        }

        val parentResult = parent?.resolve(reference, vars) ?: return null
        if (shouldBeResolved && parentResult is LibraryDescriptor) {
            save(reference, parentResult)
        }

        return parentResult
    }

    /**
     * Matches a list of actual library arguments with declared library parameters
     * Arguments can be named or not. Named arguments should be placed after unnamed
     * Parameters may have default value
     *
     * @return A name-to-value map of library arguments
     */
    private fun substituteArguments(parameters: List<Variable>, arguments: List<Variable>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (arguments.any { it.name.isEmpty() }) {
            if (parameters.count() != 1) {
                throw ReplCompilerException("Unnamed argument is allowed only if library has a single property")
            }
            if (arguments.count() != 1) {
                throw ReplCompilerException("Too many arguments")
            }
            result[parameters[0].name] = arguments[0].value
            return result
        }

        arguments.forEach {
            result[it.name] = it.value
        }
        parameters.forEach {
            if (!result.containsKey(it.name)) {
                result[it.name] = it.value
            }
        }
        return result
    }

    private fun processDescriptor(library: LibraryDescriptor, mapping: Map<String, String>): LibraryDefinition {
        return LibraryDefinitionImpl(
            dependencies = library.dependencies.replaceVariables(mapping),
            repositories = library.repositories.replaceVariables(mapping),
            imports = library.imports.replaceVariables(mapping),
            init = library.init.replaceVariables(mapping),
            shutdown = library.shutdown.replaceVariables(mapping),
            initCell = library.initCell.replaceVariables(mapping),
            renderers = library.renderers.replaceVariables(mapping),
            converters = library.converters.replaceVariables(mapping),
            minKernelVersion = library.minKernelVersion
        )
    }
}

class FallbackLibraryResolver : LibraryDescriptorResolver() {
    override fun tryResolve(reference: LibraryReference): LibraryDescriptor {
        return reference.resolve()
    }

    override fun save(reference: LibraryReference, descriptor: LibraryDescriptor) {
        // fallback resolver doesn't cache results
    }
}

class LocalLibraryResolver(
    parent: LibraryResolver?,
    mainLibrariesDir: String?
) : LibraryDescriptorResolver(parent) {
    private val logger = getLogger()
    private val pathsToCheck: List<String>

    init {
        val localSettingsPath = Paths.get(System.getProperty("user.home"), ".jupyter_kotlin").toString()
        val paths = mutableListOf(
            Paths.get(localSettingsPath, LocalCacheDir).toString(),
            Paths.get(localSettingsPath, LibrariesDir).toString()
        )
        mainLibrariesDir?.let { paths.add(it) }

        pathsToCheck = paths
    }

    override fun shouldResolve(reference: LibraryReference): Boolean {
        return reference.shouldBeCachedLocally
    }

    override fun tryResolve(reference: LibraryReference): LibraryDescriptor? {
        val jsons = pathsToCheck.mapNotNull { dir ->
            val file = reference.getFile(dir)
            if (!file.exists()) null
            else { file.readText() }
        }

        if (jsons.size > 1) {
            logger.warn("More than one file for library $reference found in local cache directories")
        }

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
