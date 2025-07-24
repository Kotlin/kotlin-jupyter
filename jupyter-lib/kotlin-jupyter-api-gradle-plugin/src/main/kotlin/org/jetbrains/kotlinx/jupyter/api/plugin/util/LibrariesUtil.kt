package org.jetbrains.kotlinx.jupyter.api.plugin.util

data class FQNAware(
    val fqn: String,
)

class LibrariesScanResult(
    val definitions: Set<FQNAware> = emptySet(),
    val producers: Set<FQNAware> = emptySet(),
)

val emptyScanResult = LibrariesScanResult()
