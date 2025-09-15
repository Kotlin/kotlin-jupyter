package org.jetbrains.kotlinx.jupyter.api.dependencies

import java.io.File

/**
 * Represents the result of a dependency resolution operation.
 *
 * This sealed class is used to encapsulate either the successful resolution of dependencies
 * or the failure encountered during the resolution process.
 */
sealed class ResolutionResult {
    class Success(
        val binaryClasspath: List<File>,
        val sourceClasspath: List<File>,
    ) : ResolutionResult()

    class Failure(
        val message: String,
    ) : ResolutionResult()
}
