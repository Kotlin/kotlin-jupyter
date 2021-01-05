package org.jetbrains.kotlinx.jupyter.api.libraries

import kotlinx.serialization.Serializable

enum class ResourcePathType {
    /**
     * URL resource.
     * Examples:
     * - https://github.com/Kotlin/kotlin-jupyter/lib.js
     * - file:///C:/Users/lib.js
     */
    URL,

    /**
     * Local resource.
     * Examples:
     * - /usr/lib/lib.js
     * - libs/lib.js
     */
    LOCAL_PATH,

    /**
     * Path which should be resolved as resource by current classpath
     * Example:
     * - META-INF/libs/lib.js
     */
    CLASSPATH_PATH,
}

enum class ResourceType {
    JS,
    CSS
}

@Serializable
data class ResourceLocation(
    val path: String,
    val type: ResourcePathType,
)

@Serializable
data class LibraryResource(
    val locations: List<ResourceLocation>,
    val type: ResourceType,
    val name: String,
)
