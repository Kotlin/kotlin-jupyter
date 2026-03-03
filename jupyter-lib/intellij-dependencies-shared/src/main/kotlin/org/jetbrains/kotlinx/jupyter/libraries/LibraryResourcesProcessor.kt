package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResource

interface LibraryResourcesProcessor {
    fun wrapLibrary(
        resource: LibraryResource,
        classLoader: ClassLoader,
    ): String
}
