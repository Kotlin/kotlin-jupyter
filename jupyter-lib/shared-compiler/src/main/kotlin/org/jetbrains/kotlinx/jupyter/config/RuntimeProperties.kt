package org.jetbrains.kotlinx.jupyter.config

import jupyter.kotlin.JavaRuntime
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.libraries.DefaultLibraryDescriptorGlobalOptions
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptor
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptorGlobalOptions
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryDescriptor
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryDescriptorGlobalOptions
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig

fun String.parseIniConfig() =
    lineSequence().map { it.split('=') }.filter { it.count() == 2 }.map { it[0] to it[1] }.toMap()

fun readResourceAsIniFile(fileName: String, classLoader: ClassLoader) =
    classLoader.getResource(fileName)?.readText()?.parseIniConfig().orEmpty()

private val correctClassLoader = KernelStreams::class.java.classLoader

val defaultRuntimeProperties by lazy {
    RuntimeKernelProperties(
        readResourceAsIniFile("kotlin-jupyter-compiler.properties", correctClassLoader),
    )
}

private const val resourcesLibraryPath = "jupyterLibraries"

val librariesFromResources: Map<String, LibraryDescriptor> by lazy {
    val listText = correctClassLoader.getResource("$resourcesLibraryPath/libraries.list")
    val logger = getLogger("Kotlin Jupyter libraries parsing")

    listText
        ?.readText()
        ?.lineSequence()
        .orEmpty()
        .filter { it.isNotEmpty() }
        .mapNotNull { descriptorFile ->
            correctClassLoader.getResource("$resourcesLibraryPath/$descriptorFile")?.readText()?.let { text ->
                val libraryName = descriptorFile.removeSuffix(".json")
                logger.info("Parsing library $libraryName from resources")
                logger.catchAll(msg = "Parsing descriptor for library '$libraryName' failed") {
                    libraryName to parseLibraryDescriptor(text)
                }
            }
        }
        .toMap()
}

val descriptorOptionsFromResources: LibraryDescriptorGlobalOptions by lazy {
    val optionsText = correctClassLoader
        .getResource("$resourcesLibraryPath/global.options")
        ?.readText() ?: return@lazy DefaultLibraryDescriptorGlobalOptions

    try {
        parseLibraryDescriptorGlobalOptions(optionsText)
    } catch (e: ReplException) {
        DefaultLibraryDescriptorGlobalOptions
    }
}

fun propertyMissingError(propertyDescription: String): Nothing {
    @Suppress("UNREACHABLE_CODE")
    return error("Compiler artifact should contain $propertyDescription")
}

val currentKernelVersion by lazy {
    defaultRuntimeProperties.version ?: propertyMissingError("kernel version")
}

val currentKotlinVersion by lazy {
    defaultRuntimeProperties.kotlinVersion
}

fun createRuntimeProperties(
    kernelConfig: KernelConfig,
    defaultProperties: ReplRuntimeProperties = defaultRuntimeProperties,
): ReplRuntimeProperties {
    return object : ReplRuntimeProperties by defaultProperties {
        override val jvmTargetForSnippets: String
            get() = kernelConfig.jvmTargetForSnippets ?: defaultProperties.jvmTargetForSnippets
    }
}

class RuntimeKernelProperties(val map: Map<String, String>) : ReplRuntimeProperties {
    override val version: KotlinKernelVersion? by lazy {
        map["version"]?.let { KotlinKernelVersion.from(it) }
    }

    @Deprecated("This parameter is meaningless, do not use")
    override val librariesFormatVersion: Int
        get() = throw RuntimeException("Libraries format version is not specified!")
    override val currentBranch: String
        get() = map["currentBranch"] ?: throw RuntimeException("Current branch is not specified!")
    override val currentSha: String
        get() = map["currentSha"] ?: throw RuntimeException("Current commit SHA is not specified!")
    override val jvmTargetForSnippets by lazy {
        map["jvmTargetForSnippets"] ?: JavaRuntime.version
    }
    override val kotlinVersion: String
        get() = map["kotlinVersion"] ?: throw RuntimeException("Kotlin version is not specified!")
}
