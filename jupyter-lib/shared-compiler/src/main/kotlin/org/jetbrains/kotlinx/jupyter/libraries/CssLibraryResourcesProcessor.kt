package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResource
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourceFallbacksBundle
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourcePathType
import org.jetbrains.kotlinx.jupyter.common.getHttp
import java.io.File
import java.io.IOException

class CssLibraryResourcesProcessor : LibraryResourcesProcessor {
    private fun loadCssAsText(bundle: ResourceFallbacksBundle, classLoader: ClassLoader): String {
        val exceptions = mutableListOf<Exception>()
        for (resourceLocation in bundle.locations) {
            val path = resourceLocation.path

            fun wrapInTag(text: String) = """
                <style>
                $text
                </style>
            """.trimIndent()

            return try {
                when (resourceLocation.type) {
                    ResourcePathType.URL -> """
                        <link rel="stylesheet" href="$path">
                    """.trimIndent()
                    ResourcePathType.URL_EMBEDDED -> wrapInTag(getHttp(path).text)
                    ResourcePathType.LOCAL_PATH -> wrapInTag(File(path).readText())
                    ResourcePathType.CLASSPATH_PATH -> wrapInTag(loadResourceFromClassLoader(path, classLoader))
                }
            } catch (e: IOException) {
                exceptions.add(e)
                continue
            }
        }

        throw Exception("No resource fallback found! Related exceptions: $exceptions")
    }

    override fun wrapLibrary(resource: LibraryResource, classLoader: ClassLoader): String {
        return resource.bundles.joinToString("\n") { loadCssAsText(it, classLoader) }
    }
}
