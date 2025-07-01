package org.jetbrains.kotlinx.jupyter.api.libraries

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.jupyter.util.ResourceBunchSerializer
import org.jetbrains.kotlinx.jupyter.util.replaceVariables

enum class ResourcePathType {
    /**
     * URL resource. Resource is attached by link.
     * Examples:
     * - https://github.com/Kotlin/kotlin-jupyter/lib.js
     * - file:///C:/Users/lib.js
     */
    URL,

    /**
     * URL resource. Resource is embedded into notebook.
     */
    URL_EMBEDDED,

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
    CSS,
}

@Serializable
data class ResourceLocation(
    val path: String,
    val type: ResourcePathType,
) : VariablesSubstitutionAware<ResourceLocation> {
    override fun replaceVariables(mapping: Map<String, String>): ResourceLocation = ResourceLocation(replaceVariables(path, mapping), type)
}

@Serializable(ResourceBunchSerializer::class)
data class ResourceFallbacksBundle(
    val locations: List<ResourceLocation>,
) : VariablesSubstitutionAware<ResourceFallbacksBundle> {
    constructor(vararg locations: ResourceLocation) : this(listOf(*locations))

    override fun replaceVariables(mapping: Map<String, String>): ResourceFallbacksBundle =
        ResourceFallbacksBundle(
            locations.map {
                it.replaceVariables(mapping)
            },
        )
}

@Serializable
data class LibraryResource(
    val bundles: List<ResourceFallbacksBundle>,
    val type: ResourceType,
    val name: String,
) : VariablesSubstitutionAware<LibraryResource> {
    override fun replaceVariables(mapping: Map<String, String>): LibraryResource =
        LibraryResource(
            bundles.map { it.replaceVariables(mapping) },
            type,
            replaceVariables(name, mapping),
        )
}
