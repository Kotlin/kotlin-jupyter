package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResource
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourceFallbacksBunch
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourcePathType
import java.io.File
import java.io.IOException

class CssLibraryResourcesProcessor : LibraryResourcesProcessor {
    private fun loadCssAsText(bunch: ResourceFallbacksBunch, classLoader: ClassLoader): String {
        val exceptions = mutableListOf<Exception>()
        for (resourceLocation in bunch.locations) {
            val path = resourceLocation.path

            fun wrapInTag(text: String) = """
                <script>
                $text
                </script>
            """.trimIndent()

            return try {
                when (resourceLocation.type) {
                    ResourcePathType.URL -> """
                        <link rel="stylesheet" href="$path">
                    """.trimIndent()
                    ResourcePathType.URL_EMBEDDED -> wrapInTag(getHttp(path).text)
                    ResourcePathType.LOCAL_PATH -> wrapInTag(File(path).readText())
                    ResourcePathType.CLASSPATH_PATH -> wrapInTag(classLoader.getResource(path)?.readText().orEmpty())
                }
            } catch (e: IOException) {
                exceptions.add(e)
                continue
            }
        }

        throw Exception("No resource fallback found! Related exceptions: $exceptions")
    }

    override fun wrapLibrary(resource: LibraryResource, classLoader: ClassLoader): String {
        return resource.bunches.joinToString("\n") { loadCssAsText(it, classLoader) }
    }
}
