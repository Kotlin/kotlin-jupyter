package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.common.HttpClient
import org.jetbrains.kotlinx.jupyter.common.LibraryDescriptorsManager
import org.jetbrains.kotlinx.jupyter.common.SimpleHttpClient

class LibraryHttpUtil(
    val httpClient: HttpClient,
    val libraryDescriptorsManager: LibraryDescriptorsManager,
    val libraryInfoCache: LibraryInfoCache,
    val libraryReferenceParser: LibraryReferenceParser,
)

fun createLibraryHttpUtil(httpClient: HttpClient = SimpleHttpClient): LibraryHttpUtil {
    val libraryDescriptorsManager = LibraryDescriptorsManager.getInstance(httpClient)
    val libraryInfoCache = LibraryInfoCacheImpl(libraryDescriptorsManager)
    val libraryReferenceParser = LibraryReferenceParserImpl(libraryInfoCache)

    return LibraryHttpUtil(
        httpClient,
        libraryDescriptorsManager,
        libraryInfoCache,
        libraryReferenceParser,
    )
}
