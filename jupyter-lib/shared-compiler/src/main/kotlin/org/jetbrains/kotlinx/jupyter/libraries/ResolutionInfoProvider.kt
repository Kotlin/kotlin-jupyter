package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionInfo

/**
 * If the type of resolution info is specified, i.e. `lib@url[https://xyz.com/lib.json]`
 * then the corresponding [LibraryResolutionInfo] is created.
 * [ResolutionInfoProvider] isn't used in this case.
 *
 * If it is not specified but still present, i.e. `lib@https://xyz.com/lib.json` then
 * [get] is used to guess and obtain correct [LibraryResolutionInfo] from
 * the string after `@`
 *
 * If nothing is specified, i.e. `lib` then [fallback] is used.
 * Implementations of [get] should consider returning [fallback] in case if string is empty.
 */
interface ResolutionInfoProvider {
    var fallback: LibraryResolutionInfo

    fun get(string: String): LibraryResolutionInfo
}
