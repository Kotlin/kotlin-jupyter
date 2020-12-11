package org.jetbrains.kotlin.jupyter.test

import jupyter.kotlin.DependsOn
import jupyter.kotlin.JavaRuntime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlin.jupyter.DisplayHandler
import org.jetbrains.kotlin.jupyter.ReplRuntimeProperties
import org.jetbrains.kotlin.jupyter.api.CodeCell
import org.jetbrains.kotlin.jupyter.api.DisplayContainer
import org.jetbrains.kotlin.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlin.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlin.jupyter.api.Notebook
import org.jetbrains.kotlin.jupyter.api.ResultsAccessor
import org.jetbrains.kotlin.jupyter.api.RuntimeUtils
import org.jetbrains.kotlin.jupyter.config.defaultRepositories
import org.jetbrains.kotlin.jupyter.defaultRuntimeProperties
import org.jetbrains.kotlin.jupyter.dependencies.ResolverConfig
import org.jetbrains.kotlin.jupyter.libraries.LibrariesDir
import org.jetbrains.kotlin.jupyter.libraries.LibraryDescriptor
import org.jetbrains.kotlin.jupyter.libraries.LibraryDescriptorExt
import org.jetbrains.kotlin.jupyter.libraries.LibraryFactory
import org.jetbrains.kotlin.jupyter.libraries.LibraryReference
import org.jetbrains.kotlin.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlin.jupyter.libraries.parseLibraryDescriptors
import org.jetbrains.kotlin.jupyter.log
import java.io.File
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContext

const val standardResolverBranch = "master"

val standardResolverRuntimeProperties = object : ReplRuntimeProperties by defaultRuntimeProperties {
    override val currentBranch: String
        get() = standardResolverBranch
}

val classpath = scriptCompilationClasspathFromContext(
    "kotlin-jupyter-lib",
    "kotlin-jupyter-api",
    "kotlin-jupyter-compiler",
    "kotlin-stdlib",
    "kotlin-reflect",
    "kotlin-script-runtime",
    classLoader = DependsOn::class.java.classLoader
)

val LibraryFactory.testResolverConfig: ResolverConfig
    get() = ResolverConfig(
        defaultRepositories,
        getResolverFromNamesMap(parseLibraryDescriptors(readLibraries()))
    )

fun Collection<Pair<String, String>>.toLibraries(libraryFactory: LibraryFactory): LibraryResolver {
    val libJsons = map { it.first to Json.decodeFromString<JsonObject>(it.second) }.toMap()
    return libraryFactory.getResolverFromNamesMap(parseLibraryDescriptors(libJsons))
}

fun LibraryFactory.getResolverFromNamesMap(map: Map<String, LibraryDescriptor>): LibraryResolver {
    return InMemoryLibraryResolver(null, map.mapKeys { entry -> LibraryReference(resolutionInfoProvider.get(), entry.key) })
}

fun readLibraries(basePath: String? = null): Map<String, JsonObject> {
    return File(basePath, LibrariesDir)
        .listFiles()?.filter { it.extension == LibraryDescriptorExt }
        ?.map {
            log.info("Loading '${it.nameWithoutExtension}' descriptor from '${it.canonicalPath}'")
            it.nameWithoutExtension to Json.decodeFromString<JsonObject>(it.readText())
        }
        .orEmpty()
        .toMap()
}

class InMemoryLibraryResolver(parent: LibraryResolver?, initialCache: Map<LibraryReference, LibraryDescriptor>? = null) : LibraryResolver(parent) {
    override val cache = hashMapOf<LibraryReference, LibraryDescriptor>()

    init {
        initialCache?.forEach { (key, value) ->
            cache[key] = value
        }
    }

    override fun shouldResolve(reference: LibraryReference): Boolean {
        return reference.shouldBeCachedInMemory
    }

    override fun tryResolve(reference: LibraryReference): LibraryDescriptor? {
        return cache[reference]
    }

    override fun save(reference: LibraryReference, descriptor: LibraryDescriptor) {
        cache[reference] = descriptor
    }
}

class TestDisplayHandler(private val list: MutableList<Any> = mutableListOf()) : DisplayHandler {
    override fun handleDisplay(value: Any) {
        list.add(value)
    }

    override fun handleUpdate(value: Any, id: String?) {
        // TODO: Implement correct updating
    }
}

class NotebookMock : Notebook<CodeCell> {
    override val cells: Map<Int, CodeCell>
        get() = emptyMap()
    override val results: ResultsAccessor
        get() = ResultsAccessor { cells[it] }
    override val displays: DisplayContainer
        get() = error("Not supposed to be called")
    override val host: KotlinKernelHost
        get() = error("Not supposed to be called")

    override fun history(before: Int): CodeCell? {
        error("Not supposed to be called")
    }

    override val kernelVersion: KotlinKernelVersion
        get() = defaultRuntimeProperties.version!!
    override val runtimeUtils: RuntimeUtils
        get() = JavaRuntime
}
