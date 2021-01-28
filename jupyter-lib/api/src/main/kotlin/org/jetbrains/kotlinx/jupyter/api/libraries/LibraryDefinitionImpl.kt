package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.AfterCellExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.ClassAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.FieldHandler
import org.jetbrains.kotlinx.jupyter.api.FileAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.RendererTypeHandler

/**
 * Trivial implementation of [LibraryDefinition].
 * You may use it in simple cases instead of overriding [LibraryDefinition]
 * to avoid additional anonymous classes creation
 */
class LibraryDefinitionImpl(
    override val dependencies: List<String> = emptyList(),
    override val repositories: List<String> = emptyList(),
    override val imports: List<String> = emptyList(),
    override val init: List<Execution<*>> = emptyList(),
    override val initCell: List<Execution<*>> = emptyList(),
    override val afterCellExecution: List<AfterCellExecutionCallback> = emptyList(),
    override val shutdown: List<Execution<*>> = emptyList(),
    override val renderers: List<RendererTypeHandler> = emptyList(),
    override val converters: List<FieldHandler> = emptyList(),
    override val classAnnotations: List<ClassAnnotationHandler> = emptyList(),
    override val fileAnnotations: List<FileAnnotationHandler> = emptyList(),
    override val resources: List<LibraryResource> = emptyList(),
    override val minKernelVersion: KotlinKernelVersion? = null,
) : LibraryDefinition
