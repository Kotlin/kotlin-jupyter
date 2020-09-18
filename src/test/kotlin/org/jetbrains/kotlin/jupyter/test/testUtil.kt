package org.jetbrains.kotlin.jupyter.test

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import jupyter.kotlin.DependsOn
import org.jetbrains.kotlin.jupyter.DisplayHandler
import org.jetbrains.kotlin.jupyter.LibrariesDir
import org.jetbrains.kotlin.jupyter.LibraryDescriptor
import org.jetbrains.kotlin.jupyter.LibraryDescriptorExt
import org.jetbrains.kotlin.jupyter.ReplRuntimeProperties
import org.jetbrains.kotlin.jupyter.ResolverConfig
import org.jetbrains.kotlin.jupyter.defaultRepositories
import org.jetbrains.kotlin.jupyter.defaultRuntimeProperties
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
    "jupyter-lib",
    "kotlin-jupyter-api",
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
    val parser = Parser.default()
    val libJsons = map { it.first to parser.parse(StringBuilder(it.second)) as JsonObject }.toMap()
    return libraryFactory.getResolverFromNamesMap(parseLibraryDescriptors(libJsons))
}

fun LibraryFactory.getResolverFromNamesMap(map: Map<String, LibraryDescriptor>): LibraryResolver {
    return InMemoryLibraryResolver(null, map.mapKeys { entry -> LibraryReference(resolutionInfoProvider.get(), entry.key) })
}

fun readLibraries(basePath: String? = null): Map<String, JsonObject> {
    val parser = Parser.default()
    return File(basePath, LibrariesDir)
        .listFiles()?.filter { it.extension == LibraryDescriptorExt }
        ?.map {
            log.info("Loading '${it.nameWithoutExtension}' descriptor from '${it.canonicalPath}'")
            it.nameWithoutExtension to parser.parse(it.canonicalPath) as JsonObject
        }
        .orEmpty()
        .toMap()
}

class InMemoryLibraryResolver(parent: LibraryResolver?, initialCache: Map<LibraryReference, LibraryDescriptor>? = null) : LibraryResolver(parent) {
    override val cache = hashMapOf<LibraryReference, LibraryDescriptor>()

    init {
        initialCache?.forEach { key, value ->
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
