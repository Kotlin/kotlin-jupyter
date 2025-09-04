package org.jetbrains.kotlinx.jupyter.api.dependencies

/**
 * Represents a single dependency description.
 *
 * Could be either a Maven coordinate or a simple file name.
 */
@JvmInline
value class DependencyDescription(
    val description: String,
)
