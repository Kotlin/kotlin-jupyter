package org.jetbrains.kotlinx.jupyter.api

import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesScanResult

interface LibraryLoader {
    fun addLibrariesByScanResult(
        host: KotlinKernelHost,
        notebook: Notebook,
        classLoader: ClassLoader,
        libraryOptions: Map<String, String> = mapOf(),
        scanResult: LibrariesScanResult,
    )
}
