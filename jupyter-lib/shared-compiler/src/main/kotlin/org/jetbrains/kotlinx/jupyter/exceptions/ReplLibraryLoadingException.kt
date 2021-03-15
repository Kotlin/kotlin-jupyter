package org.jetbrains.kotlinx.jupyter.exceptions

class ReplLibraryLoadingException(
    libraryName: String? = null,
    message: String? = null,
    cause: Throwable? = null,
) : ReplException("Error loading library${libraryName?.let { " $it" }.orEmpty()}" + message?.let { ": $it" }.orEmpty(), cause)
