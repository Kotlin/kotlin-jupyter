package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.getLogger
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.config.kernelClassLoader
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException

interface LibraryDescriptorsProvider {
    fun getDescriptors(): Map<String, LibraryDescriptor>

    fun getDescriptorForVersionsCompletion(fullName: String): LibraryDescriptor? = getDescriptors()[fullName]

    fun getDescriptorGlobalOptions(): LibraryDescriptorGlobalOptions = DefaultLibraryDescriptorGlobalOptions
}

open class ResourceLibraryDescriptorsProvider(
    loggerFactory: KernelLoggerFactory,
) : LibraryDescriptorsProvider {
    private val logger = loggerFactory.getLogger(this::class)

    private val librariesFromResources: Map<String, LibraryDescriptor> by lazy {
        val listText = kernelClassLoader.getResource("$RESOURCES_LIBRARY_PATH/libraries.list")

        listText
            ?.readText()
            ?.lineSequence()
            .orEmpty()
            .filter { it.isNotEmpty() }
            .mapNotNull { descriptorFile ->
                kernelClassLoader.getResource("$RESOURCES_LIBRARY_PATH/$descriptorFile")?.readText()?.let { text ->
                    val libraryName = descriptorFile.removeSuffix(".json")
                    logger.info("Parsing library $libraryName from resources")
                    logger.catchAll(msg = "Parsing descriptor for library '$libraryName' failed") {
                        libraryName to parseLibraryDescriptor(text)
                    }
                }
            }.toMap()
    }

    private val descriptorOptionsFromResources: LibraryDescriptorGlobalOptions by lazy {
        val optionsText =
            kernelClassLoader
                .getResource("$RESOURCES_LIBRARY_PATH/global.options")
                ?.readText() ?: return@lazy DefaultLibraryDescriptorGlobalOptions

        try {
            parseLibraryDescriptorGlobalOptions(optionsText)
        } catch (e: ReplException) {
            DefaultLibraryDescriptorGlobalOptions
        }
    }

    override fun getDescriptors(): Map<String, LibraryDescriptor> = librariesFromResources

    override fun getDescriptorGlobalOptions(): LibraryDescriptorGlobalOptions = descriptorOptionsFromResources

    companion object {
        private const val RESOURCES_LIBRARY_PATH = "jupyterLibraries"
    }
}
