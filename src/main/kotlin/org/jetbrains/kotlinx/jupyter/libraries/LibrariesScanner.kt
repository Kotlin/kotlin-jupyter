package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.LibraryLoader
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

class LibrariesScanner : LibraryLoader {
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
                integrationTypeNameRules.accepts(typeName),
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
        notebook: Notebook,
        libraryOptions: Map<String, String> = mapOf(),
        integrationTypeNameRules: List<AcceptanceRule<TypeName>> = listOf(),
    ) {
        val scanResult = scanForLibraries(classLoader, host, integrationTypeNameRules)
        log.debug("Scanning for libraries is done. Detected FQNs: ${Json.encodeToString(scanResult)}")
        val libraries = instantiateLibraries(classLoader, scanResult, notebook, libraryOptions)
        log.debug("Number of detected definitions: ${libraries.size}")
        host.addLibraries(libraries)
    }

    override fun addLibrariesByScanResult(
        host: KotlinKernelHost,
        notebook: Notebook,
        classLoader: ClassLoader,
        libraryOptions: Map<String, String>,
        scanResult: LibrariesScanResult,
    ) {
        host.scheduleExecution {
            val libraries = instantiateLibraries(classLoader, scanResult, notebook, libraryOptions)
            host.addLibraries(libraries)
        }
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
        val arguments = listOf(notebook, libraryOptions)

        fun <T> withErrorsHandling(declaration: LibrariesInstantiable<*>, action: () -> T): T {
            return try {
                action()
            } catch (e: Throwable) {
                val errorMessage = "Failed to load library integration class '${declaration.fqn}'"
                log.errorForUser(message = errorMessage, throwable = e)
                throw ReplException(errorMessage, e)
            }
        }

        scanResult.definitions.mapNotNullTo(definitions) { declaration ->
            withErrorsHandling(declaration) {
                instantiate(classLoader, declaration, arguments)
            }
        }

        scanResult.producers.forEach { declaration ->
            withErrorsHandling(declaration) {
                instantiate(classLoader, declaration, arguments)?.apply {
                    getDefinitions(notebook).forEach {
                        definitions.add(it)
                    }
                }
            }
        }
        return definitions
    }

    private fun <T> instantiate(
        classLoader: ClassLoader,
        data: LibrariesInstantiable<T>,
        arguments: List<Any>,
    ): T? {
        val clazz = classLoader.loadClass(data.fqn)
        if (clazz == null) {
            log.warn("Library ${data.fqn} wasn't found in classloader $classLoader")
            return null
        }

        @Suppress("UNCHECKED_CAST")
        return clazz.instantiate(arguments) as T
    }

    private fun Class<*>.instantiate(arguments: List<Any>): Any {
        val obj = kotlin.objectInstance
        if (obj != null) return obj

        val argsCount = arguments.size
        val myConstructors = constructors
            .sortedByDescending { it.parameterCount }

        val errorStringBuilder = StringBuilder()
        for (constructor in myConstructors) {
            val parameterCount = constructor.parameterCount
            if (parameterCount > argsCount) {
                errorStringBuilder.appendLine("\t$constructor: more than $argsCount parameters")
                continue
            }

            val isSuitable = constructor.parameterTypes
                .zip(arguments)
                .all { (paramType, arg) -> paramType.isInstance(arg) }

            if (!isSuitable) {
                errorStringBuilder.appendLine("\t$constructor: wrong parameter types")
                continue
            }
            return constructor.newInstance(*arguments.take(parameterCount).toTypedArray())
        }

        val notFoundReason =
            if (myConstructors.isEmpty()) "no single constructor found"
            else "no single constructor is applicable\n$errorStringBuilder"

        throw ReplException("No suitable constructor found. Reason: $notFoundReason")
    }

    companion object {
        private val log = getLogger("libraries scanning")
    }
}
