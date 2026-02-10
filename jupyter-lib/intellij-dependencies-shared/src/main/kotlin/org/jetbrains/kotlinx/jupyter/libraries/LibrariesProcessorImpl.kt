package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionRequest
import org.jetbrains.kotlinx.jupyter.magics.splitLibraryCalls

class LibrariesProcessorImpl(
    private val libraryResolver: LibraryResolver?,
    private val libraryReferenceParser: LibraryReferenceParser,
    private val kernelVersion: KotlinKernelVersion?,
) : LibrariesProcessor {
    private val _requests = mutableListOf<LibraryResolutionRequest>()
    override val requests: Collection<LibraryResolutionRequest>
        get() = _requests

    private fun checkKernelVersionRequirements(
        name: String,
        library: LibraryDefinition,
    ) {
        library.minKernelVersion?.let { minVersion ->
            kernelVersion?.let { currentVersion ->
                if (currentVersion < minVersion) {
                    throw ReplException(
                        """
                        Library '$name' requires at least $minVersion version of kernel.
                        Current kernel version is $currentVersion.
                        Please update kernel, see https://github.com/Kotlin/kotlin-jupyter#updating for more info.
                        """.trimIndent(),
                    )
                }
            }
        }
    }

    override fun processNewLibraries(arg: String): List<LibraryDefinitionProducer> =
        splitLibraryCalls(arg).map {
            val (libRef, arguments) = libraryReferenceParser.parseReferenceWithArgs(it)

            val wasRequestedBefore =
                _requests.any { request ->
                    request.reference == libRef && request.arguments == arguments
                }
            if (wasRequestedBefore) return@map EmptyLibraryDefinitionProducer

            val library =
                libraryResolver?.resolve(libRef, arguments)
                    ?: throw ReplException("Unknown library '$libRef'")

            _requests.add(LibraryResolutionRequest(libRef, arguments, library))

            checkKernelVersionRequirements(libRef.toString(), library)

            TrivialLibraryDefinitionProducer(library)
        }
}
