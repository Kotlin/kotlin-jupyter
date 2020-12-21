package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer

class LibrariesScanner {
    private val processedFQNs = mutableSetOf<FQN>()

    private fun <T, I : Instantiable<T>> Iterable<I>.filterProcessed(): List<I> {
        return filter { it.fqn !in processedFQNs }
    }

    fun addLibrariesFromClassLoader(classLoader: ClassLoader, notebook: Notebook<*>) {
        val scanResult = scanForLibraries(classLoader)
        updateProcessed(scanResult)
        val libraries = instantiateLibraries(classLoader, scanResult, notebook)
        libraries.forEach { notebook.host.addLibrary(it) }
    }

    private fun scanForLibraries(classLoader: ClassLoader): ScanResult {
        val results = classLoader.getResources(LIBRARIES_DEFINITIONS_PATH).toList().map { url ->
            val contents = url.readText()
            Json.decodeFromString<ScanResult>(contents)
        }

        val definitions = mutableListOf<DefinitionDeclaration>()
        val producers = mutableListOf<ProducerDeclaration>()

        for (result in results) {
            definitions.addAll(result.definitions)
            producers.addAll(result.producers)
        }

        return ScanResult(
            definitions.filterProcessed(),
            producers.filterProcessed(),
        )
    }

    private fun updateProcessed(scanResult: ScanResult) {
        scanResult.apply {
            val instantiableData = producers + definitions
            instantiableData.map {
                it.fqn
            }.let {
                processedFQNs.addAll(it)
            }
        }
    }

    private fun instantiateLibraries(classLoader: ClassLoader, scanResult: ScanResult, notebook: Notebook<*>): List<LibraryDefinition> {
        val definitions = mutableListOf<LibraryDefinition>()

        scanResult.definitions.mapTo(definitions) {
            instantiate(classLoader, it, notebook)
        }

        scanResult.producers.flatMapTo(definitions) {
            instantiate(classLoader, it, notebook).getDefinitions(notebook)
        }
        return definitions
    }

    private fun <T> instantiate(classLoader: ClassLoader, data: Instantiable<T>, notebook: Notebook<*>): T {
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

    @Serializable
    private class ScanResult(
        val definitions: List<DefinitionDeclaration> = emptyList(),
        val producers: List<ProducerDeclaration> = emptyList(),
    )

    @Serializable
    private class DefinitionDeclaration(
        override val fqn: FQN,
    ) : Instantiable<LibraryDefinition>

    @Serializable
    private class ProducerDeclaration(
        override val fqn: FQN,
    ) : Instantiable<LibraryDefinitionProducer>

    private interface Instantiable<T> {
        val fqn: FQN
    }

    companion object {
        const val LIBRARIES_DEFINITIONS_PATH = "META-INF/kotlin-jupyter-libraries/libraries.json"
    }
}
