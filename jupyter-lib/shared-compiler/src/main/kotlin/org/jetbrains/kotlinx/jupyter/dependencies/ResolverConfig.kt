package org.jetbrains.kotlinx.jupyter.dependencies

import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import kotlin.script.experimental.dependencies.RepositoryCoordinates

data class ResolverConfig(
    val repositories: List<RepositoryCoordinates>,
    val libraries: LibraryResolver
)
