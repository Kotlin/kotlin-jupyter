package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.GenerativeTypeHandler
import org.jetbrains.kotlinx.jupyter.api.RendererTypeHandler

interface LibraryDefinition {
    /**
     * List of artifact dependencies in gradle colon-separated format
     */
    val dependencies: List<String>
        get() = emptyList()

    /**
     * List of repository URLs to resolve dependencies in
     */
    val repositories: List<String>
        get() = emptyList()

    /**
     * List of imports: simple and star imports are both allowed
     */
    val imports: List<String>
        get() = emptyList()

    /**
     * List of code snippets evaluated on library initialization
     */
    val init: List<Execution>
        get() = emptyList()

    /**
     * List of code snippets evaluated before every cell evaluation
     */
    val initCell: List<Execution>
        get() = emptyList()

    /**
     * List of code snippets evaluated on kernel shutdown
     */
    val shutdown: List<Execution>
        get() = emptyList()

    /**
     * List of type renderers. Consider using [org.jetbrains.kotlinx.jupyter.api.Renderable]
     * as it's generally more convenient
     */
    val renderers: List<RendererTypeHandler>
        get() = emptyList()

    /**
     * List of type converters used by type providers processors
     */
    val converters: List<GenerativeTypeHandler>
        get() = emptyList()

    /**
     * List of type annotations used by annotations processor
     */
    val annotations: List<GenerativeTypeHandler>
        get() = emptyList()
}
