package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.TypeName
import org.jetbrains.kotlinx.jupyter.api.libraries.KOTLIN_JUPYTER_LIBRARIES_FILE_NAME
import org.jetbrains.kotlinx.jupyter.api.libraries.KOTLIN_JUPYTER_RESOURCES_PATH
import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesDefinitionDeclaration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesInstantiable
import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesProducerDeclaration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesScanResult
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.config.errorForUser
import org.jetbrains.kotlinx.jupyter.config.getLogger
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.util.AcceptanceRule
import org.jetbrains.kotlinx.jupyter.util.accepts
import org.jetbrains.kotlinx.jupyter.util.unionAcceptance

class LibrariesScanner(val notebook: Notebook) {
    private val processedFQNs = mutableSetOf<TypeName>()
    private val discardedFQNs = mutableSetOf<TypeName>()

    private fun <I : LibrariesInstantiable<*>> Iterable<I>.filterNamesToLoad(
        host: KotlinKernelHost,
        integrationTypeNameRules: Iterable<AcceptanceRule<TypeName>>,
    ): List<I> {
        return filter {
            val typeName = it.fqn
            val acceptance = unionAcceptance(
                host.acceptsIntegrationTypeName(typeName),
                integrationTypeNameRules.accepts(typeName)
            )
            log.debug("Acceptance result for $typeName: $acceptance")
            when (acceptance) {
                true -> processedFQNs.add(typeName)
                false -> {
                    discardedFQNs.add(typeName)
                    false
                }
                null -> typeName !in discardedFQNs && processedFQNs.add(typeName)
            }
        }
    }

    fun addLibrariesFromClassLoader(
        classLoader: ClassLoader,
        host: KotlinKernelHost,
        libraryOptions: Map<String, String> = mapOf(),
        integrationTypeNameRules: List<AcceptanceRule<TypeName>> = listOf()
    ) {
        val scanResult = scanForLibraries(classLoader, host, integrationTypeNameRules)
        log.debug("Scanning for libraries is done. Detected FQNs: ${Json.encodeToString(scanResult)}")
        val libraries = instantiateLibraries(classLoader, scanResult, notebook, libraryOptions)
        log.debug("Number of detected definitions: ${libraries.size}")
        host.addLibraries(libraries)
    }

    private fun scanForLibraries(classLoader: ClassLoader, host: KotlinKernelHost, integrationTypeNameRules: List<AcceptanceRule<TypeName>> = listOf()): LibrariesScanResult {
        val results = classLoader.getResources("$KOTLIN_JUPYTER_RESOURCES_PATH/$KOTLIN_JUPYTER_LIBRARIES_FILE_NAME").toList().map { url ->
            val contents = url.readText()
            Json.decodeFromString<LibrariesScanResult>(contents)
        }

        val definitions = mutableListOf<LibrariesDefinitionDeclaration>()
        val producers = mutableListOf<LibrariesProducerDeclaration>()

        for (result in results) {
            definitions.addAll(result.definitions)
            producers.addAll(result.producers)
        }

        fun <I : LibrariesInstantiable<*>> Iterable<I>.filterNames() = filterNamesToLoad(host, integrationTypeNameRules)

        return LibrariesScanResult(
            definitions.filterNames(),
            producers.filterNames(),
        )
    }

    private fun instantiateLibraries(
        classLoader: ClassLoader,
        scanResult: LibrariesScanResult,
        notebook: Notebook,
        libraryOptions: Map<String, String>,
    ): List<LibraryDefinition> {
        val definitions = mutableListOf<LibraryDefinition>()

        fun <T> withErrorsHandling(declaration: LibrariesInstantiable<*>, action: () -> T): T {
            return try {
                action()
            } catch (e: Throwable) {
                val errorMessage = "Failed to load library integration class '${declaration.fqn}'"
                log.errorForUser(message = errorMessage, throwable = e)
                throw ReplException(errorMessage, e)
            }
        }

        scanResult.definitions.mapTo(definitions) { declaration ->
            withErrorsHandling(declaration) {
                instantiate(classLoader, declaration, notebook, libraryOptions)
            }
        }

        scanResult.producers.forEach { declaration ->
            withErrorsHandling(declaration) {
                val producer = instantiate(classLoader, declaration, notebook, libraryOptions)
                producer.getDefinitions(notebook).forEach {
                    definitions.add(it)
                }
            }
        }
        return definitions
    }

    private fun <T> instantiate(
        classLoader: ClassLoader,
        data: LibrariesInstantiable<T>,
        notebook: Notebook,
        libraryOptions: Map<String, String>
    ): T {
        val clazz = classLoader.loadClass(data.fqn)
        val constructors = clazz.constructors

        if (constructors.isEmpty()) {
            @Suppress("UNCHECKED_CAST")
            return clazz.kotlin.objectInstance as T
        }

        val constructor = constructors.single()

        @Suppress("UNCHECKED_CAST")
        return when (constructor.parameterCount) {
            0 -> {
                constructor.newInstance()
            }
            1 -> {
                constructor.newInstance(notebook)
            }
            2 -> {
                constructor.newInstance(notebook, libraryOptions)
            }
            else -> throw IllegalStateException("Only zero, one or two arguments are allowed for library class")
        } as T
    }

    companion object {
        private val log = getLogger("libraries scanning")
    }
}
