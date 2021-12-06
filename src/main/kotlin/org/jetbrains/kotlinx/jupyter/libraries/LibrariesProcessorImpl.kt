package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionRequest
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException

class LibrariesProcessorImpl(
    private val libraries: LibraryResolver?,
    private val kernelVersion: KotlinKernelVersion?,
) : LibrariesProcessor {

    private val _requests = mutableListOf<LibraryResolutionRequest>()
    override val requests: Collection<LibraryResolutionRequest>
        get() = _requests

    private fun checkKernelVersionRequirements(name: String, library: LibraryDefinition) {
        library.minKernelVersion?.let { minVersion ->
            kernelVersion?.let { currentVersion ->
                if (currentVersion < minVersion) {
                    throw ReplException("Library '$name' requires at least $minVersion version of kernel. Current kernel version is $currentVersion. Please update kernel")
                }
            }
        }
    }

    override fun processNewLibraries(arg: String): List<LibraryDefinitionProducer> =
        splitLibraryCalls(arg).map {
            val (libRef, vars) = parseReferenceWithArgs(it)
            val library = libraries?.resolve(libRef, vars)
                ?: throw ReplException("Unknown library '$libRef'")

            _requests.add(LibraryResolutionRequest(libRef, vars, library))

            checkKernelVersionRequirements(libRef.toString(), library)

            TrivialLibraryDefinitionProducer(library)
        }
}
