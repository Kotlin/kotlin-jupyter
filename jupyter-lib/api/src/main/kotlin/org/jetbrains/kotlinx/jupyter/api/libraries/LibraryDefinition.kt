package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.AfterCellExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.ClassAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.FieldHandler
import org.jetbrains.kotlinx.jupyter.api.FileAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.RendererTypeHandler

/**
 * Library definition represents "library" concept in Kotlin kernel.
 */
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
    val init: List<Execution<*>>
        get() = emptyList()

    /**
     * List of code snippets evaluated before every cell evaluation
     */
    val initCell: List<Execution<*>>
        get() = emptyList()

    /**
     * List of callbacks called after cell evaluation
     */
    val afterCellExecution: List<AfterCellExecutionCallback>
        get() = emptyList()

    /**
     * List of code snippets evaluated on kernel shutdown
     */
    val shutdown: List<Execution<*>>
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
    val converters: List<FieldHandler>
        get() = emptyList()

    /**
     * List of type annotations used by annotations processor
     */
    val classAnnotations: List<ClassAnnotationHandler>
        get() = emptyList()

    /**
     * List of file annotation handlers
     */
    val fileAnnotations: List<FileAnnotationHandler>
        get() = emptyList()

    /**
     * List of library resources
     */
    val resources: List<LibraryResource>
        get() = emptyList()

    /**
     * Minimal kernel version that is supported by this library
     */
    val minKernelVersion: KotlinKernelVersion?

    /**
     * Original descriptor text, or **null** if it has non-textual nature
     */
    val originalDescriptorText: String?
        get() = null
}
