package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResource
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourceLocation
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourcePathType
import java.io.File

class CssLibraryResourcesProcessor : LibraryResourcesProcessor {
    private fun loadCssAsText(resource: ResourceLocation, classLoader: ClassLoader): String {
        val path = resource.path

        fun wrapInTag(text: String) = """
            <script>
            $text
            </script>
        """.trimIndent()

        return when (resource.type) {
            ResourcePathType.URL -> """
                <link rel="stylesheet" href="$path">
            """.trimIndent()
            ResourcePathType.LOCAL_PATH -> wrapInTag(File(path).readText())
            ResourcePathType.CLASSPATH_PATH -> wrapInTag(classLoader.getResource(path)?.readText().orEmpty())
        }
    }

    override fun wrapLibrary(resource: LibraryResource, classLoader: ClassLoader): String {
        return resource.locations.joinToString("\n") { loadCssAsText(it, classLoader) }
    }
}
