package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.AnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.GenerativeTypeHandler
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
        override val init: List<Execution> = emptyList(),
        override val initCell: List<Execution> = emptyList(),
        override val shutdown: List<Execution> = emptyList(),
        override val renderers: List<RendererTypeHandler> = emptyList(),
        override val converters: List<GenerativeTypeHandler> = emptyList(),
        override val annotations: List<AnnotationHandler> = emptyList(),
        override val resources: List<LibraryResource> = emptyList(),
) : LibraryDefinition
