package org.jetbrains.kotlinx.jupyter.dependencies

import kotlin.script.experimental.api.SourceCode

/**
 * A request to resolve a single artifact.
 *
 * @property artifact Artifact coordinates or path as provided by the user/script.
 * @property sourceCodeLocation Optional location in the source code where this request originated; used for diagnostics.
 */
data class ArtifactRequest(
    val artifact: String,
    val sourceCodeLocation: SourceCode.LocationWithId?,
)
