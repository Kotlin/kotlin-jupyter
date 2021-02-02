package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.decodeFromString
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

class LibrariesScanner(val notebook: Notebook) {
    private val processedFQNs = mutableSetOf<TypeName>()

    private fun <T, I : LibrariesInstantiable<T>> Iterable<I>.filterProcessed(): List<I> {
        return filter { it.fqn !in processedFQNs }
    }

    fun addLibrariesFromClassLoader(classLoader: ClassLoader, host: KotlinKernelHost) {
        val scanResult = scanForLibraries(classLoader)
        updateProcessed(scanResult)
        val libraries = instantiateLibraries(classLoader, scanResult, notebook)
        libraries.forEach { host.addLibrary(it) }
    }

    private fun scanForLibraries(classLoader: ClassLoader): LibrariesScanResult {
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

        return LibrariesScanResult(
            definitions.filterProcessed(),
            producers.filterProcessed(),
        )
    }

    private fun updateProcessed(scanResult: LibrariesScanResult) {
        scanResult.apply {
            val instantiableData = producers + definitions
            instantiableData.map {
                it.fqn
            }.let {
                processedFQNs.addAll(it)
            }
        }
    }

    private fun instantiateLibraries(classLoader: ClassLoader, scanResult: LibrariesScanResult, notebook: Notebook): List<LibraryDefinition> {
        val definitions = mutableListOf<LibraryDefinition>()

        scanResult.definitions.mapTo(definitions) { declaration ->
            instantiate(classLoader, declaration, notebook)
        }

        scanResult.producers.forEach { declaration ->
            try {
                val producer = instantiate(classLoader, declaration, notebook)
                producer.getDefinitions(notebook).forEach {
                    definitions.add(it)
                }
            } catch (e: Throwable) {
                System.err.println("Failed to load library integration class '${declaration.fqn}': " + e.message)
            }
        }
        return definitions
    }

    private fun <T> instantiate(classLoader: ClassLoader, data: LibrariesInstantiable<T>, notebook: Notebook): T {
        val clazz = classLoader.loadClass(data.fqn)
        val constructor = clazz.constructors.single()

        @Suppress("UNCHECKED_CAST")
        return when (constructor.parameterCount) {
            0 -> {
                constructor.newInstance()
            }
            1 -> {
                constructor.newInstance(notebook)
            }
            else -> throw IllegalStateException("Only zero or one argument is allowed for library class")
        } as T
    }
}
