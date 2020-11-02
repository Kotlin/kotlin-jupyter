package org.jetbrains.kotlin.jupyter.libraries

import org.jetbrains.kotlin.jupyter.api.Execution
import org.jetbrains.kotlin.jupyter.api.GenerativeTypeHandler
import org.jetbrains.kotlin.jupyter.api.LibraryDefinition
import org.jetbrains.kotlin.jupyter.api.RendererTypeHandler

class LibraryDefinitionImpl(
    override val dependencies: List<String>,
    override val repositories: List<String>,
    override val imports: List<String>,
    override val init: List<Execution>,
    override val initCell: List<Execution>,
    override val shutdown: List<Execution>,
    override val renderers: List<RendererTypeHandler>,
    override val converters: List<GenerativeTypeHandler>,
    override val annotations: List<GenerativeTypeHandler>,
) : LibraryDefinition
