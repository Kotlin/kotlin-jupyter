package org.jetbrains.kotlinx.jupyter.test

import jupyter.kotlin.DependsOn
import jupyter.kotlin.JavaRuntime
import org.jetbrains.kotlinx.jupyter.CodeCellImpl
import org.jetbrains.kotlinx.jupyter.DisplayHandler
import org.jetbrains.kotlinx.jupyter.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.api.CodeCell
import org.jetbrains.kotlinx.jupyter.api.DisplayContainer
import org.jetbrains.kotlinx.jupyter.api.DisplayResultWithCell
import org.jetbrains.kotlinx.jupyter.api.JREInfoProvider
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.config.defaultRepositories
import org.jetbrains.kotlinx.jupyter.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.dependencies.ResolverConfig
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesDir
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptor
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptorExt
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptorResolver
import org.jetbrains.kotlinx.jupyter.libraries.LibraryReference
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolutionInfo
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.Variable
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryDescriptors
import org.jetbrains.kotlinx.jupyter.log
import java.io.File
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContext

const val standardResolverBranch = "master"

val standardResolverRuntimeProperties = object : ReplRuntimeProperties by defaultRuntimeProperties {
    override val currentBranch: String
        get() = standardResolverBranch
}

val classpath = scriptCompilationClasspathFromContext(
    "lib",
    "api",
    "shared-compiler",
    "kotlin-stdlib",
    "kotlin-reflect",
    "kotlin-script-runtime",
    classLoader = DependsOn::class.java.classLoader
)

val testResolverConfig: ResolverConfig
    get() = ResolverConfig(
        defaultRepositories,
        getResolverFromNamesMap(parseLibraryDescriptors(readLibraries()))
    )

fun Collection<Pair<String, String>>.toLibraries(): LibraryResolver {
    val libJsons = map { it.first to it.second }.toMap()
    return getResolverFromNamesMap(parseLibraryDescriptors(libJsons))
}

@JvmName("toLibrariesStringLibraryDefinition")
fun Collection<Pair<String, LibraryDefinition>>.toLibraries() = getResolverFromNamesMap(definitions = toMap())

fun getResolverFromNamesMap(
    descriptors: Map<String, LibraryDescriptor>? = null,
    definitions: Map<String, LibraryDefinition>? = null,
): LibraryResolver {
    return InMemoryLibraryResolver(
        null,
        descriptors?.mapKeys { entry -> LibraryReference(LibraryResolutionInfo.Default(), entry.key) },
        definitions?.mapKeys { entry -> LibraryReference(LibraryResolutionInfo.Default(), entry.key) },
    )
}

fun readLibraries(basePath: String? = null): Map<String, String> {
    return File(basePath, LibrariesDir)
        .listFiles()?.filter { it.extension == LibraryDescriptorExt }
        ?.map {
            log.info("Loading '${it.nameWithoutExtension}' descriptor from '${it.canonicalPath}'")
            it.nameWithoutExtension to it.readText()
        }
        .orEmpty()
        .toMap()
}

class InMemoryLibraryResolver(
    parent: LibraryResolver?,
    initialDescriptorsCache: Map<LibraryReference, LibraryDescriptor>? = null,
    initialDefinitionsCache: Map<LibraryReference, LibraryDefinition>? = null,
) : LibraryDescriptorResolver(parent) {
    private val definitionsCache = hashMapOf<LibraryReference, LibraryDefinition>()
    private val descriptorsCache = hashMapOf<LibraryReference, LibraryDescriptor>()

    init {
        initialDescriptorsCache?.forEach { (key, value) ->
            descriptorsCache[key] = value
        }
        initialDefinitionsCache?.forEach { (key, value) ->
            definitionsCache[key] = value
        }
    }

    override fun shouldResolve(reference: LibraryReference): Boolean {
        return reference.shouldBeCachedInMemory
    }

    override fun tryResolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition? {
        return definitionsCache[reference] ?: descriptorsCache[reference]?.convertToDefinition(arguments)
    }

    override fun save(reference: LibraryReference, definition: LibraryDefinition) {
        definitionsCache[reference] = definition
    }
}

class TestDisplayHandler(val list: MutableList<Any> = mutableListOf()) : DisplayHandler {
    override fun handleDisplay(value: Any) {
        list.add(value)
    }

    override fun handleUpdate(value: Any, id: String?) {
        // TODO: Implement correct updating
    }
}

object NotebookMock : Notebook {
    private val cells = hashMapOf<Int, CodeCellImpl>()

    override val cellsList: Collection<CodeCell>
        get() = emptyList()

    override fun getCell(id: Int): CodeCellImpl {
        return cells[id] ?: throw ArrayIndexOutOfBoundsException(
            "There is no cell with number '$id'"
        )
    }

    override fun getResult(id: Int): Any? {
        return getCell(id).result
    }

    private val displays: DisplayContainer
        get() = error("Not supposed to be called")

    override fun getAllDisplays(): List<DisplayResultWithCell> {
        return displays.getAll()
    }

    override fun getDisplaysById(id: String?): List<DisplayResultWithCell> {
        return displays.getById(id)
    }

    override fun history(before: Int): CodeCell? {
        error("Not supposed to be called")
    }

    override val kernelVersion: KotlinKernelVersion
        get() = defaultRuntimeProperties.version!!
    override val jreInfo: JREInfoProvider
        get() = JavaRuntime
}

fun library(builder: JupyterIntegration.Builder.() -> Unit): LibraryDefinition {
    val o = object : JupyterIntegration() {
        override fun Builder.onLoaded() {
            builder()
        }
    }
    return o.getDefinitions(NotebookMock).single()
}
