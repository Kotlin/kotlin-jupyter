package org.jetbrains.kotlinx.jupyter.api.libraries

/**
 * Implementations of this class provide information on how the libraries should be resolved.
 * Different implementations of `LibraryResolver` use this information for adjusting the resolution process.
 * Resolution info is encoded by a part of a [LibraryReference] that goes after `@` character
 */
interface LibraryResolutionInfo : LibraryCacheable {
    /**
     * Represents a unique identifier for a given instance of [LibraryResolutionInfo]
     * which is used for caching purposes
     */
    val key: String
}
