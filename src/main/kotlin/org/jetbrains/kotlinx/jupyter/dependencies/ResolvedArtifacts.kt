package org.jetbrains.kotlinx.jupyter.dependencies

import java.io.File

data class ResolvedArtifacts(
    val binaries: List<File> = emptyList(),
    val sources: List<File> = emptyList(),
)
