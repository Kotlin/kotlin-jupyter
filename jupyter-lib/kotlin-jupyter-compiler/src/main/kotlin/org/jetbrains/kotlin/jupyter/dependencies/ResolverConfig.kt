package org.jetbrains.kotlin.jupyter.dependencies

import org.jetbrains.kotlin.jupyter.libraries.LibraryResolver
import kotlin.script.experimental.dependencies.RepositoryCoordinates

data class ResolverConfig(
    val repositories: List<RepositoryCoordinates>,
    val libraries: LibraryResolver
)
