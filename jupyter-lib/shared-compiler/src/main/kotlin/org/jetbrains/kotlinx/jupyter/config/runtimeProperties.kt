package org.jetbrains.kotlinx.jupyter.config

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.libraries.DefaultLibraryDescriptorGlobalOptions
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptor
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptorGlobalOptions
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryDescriptor
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryDescriptorGlobalOptions

fun String.parseIniConfig() =
    lineSequence().map { it.split('=') }.filter { it.count() == 2 }.map { it[0] to it[1] }.toMap()

fun readResourceAsIniFile(fileName: String) =
    readResourceAsIniFile(fileName, ClassLoader.getSystemClassLoader())

fun readResourceAsIniFile(fileName: String, classLoader: ClassLoader) =
    classLoader.getResource(fileName)?.readText()?.parseIniConfig().orEmpty()

private val correctClassLoader = KernelStreams::class.java.classLoader

private val runtimeProperties by lazy {
    readResourceAsIniFile("kotlin-jupyter-compiler.properties", correctClassLoader)
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

fun getFromRuntimeProperties(property: String, propertyDescription: String): String {
    return runtimeProperties[property] ?: error("Compiler artifact should contain $propertyDescription")
}

val currentKernelVersion by lazy {
    KotlinKernelVersion.from(
        getFromRuntimeProperties("version", "kernel version"),
    )!!
}

val currentKotlinVersion by lazy {
    getFromRuntimeProperties("kotlinVersion", "Kotlin version")
}
