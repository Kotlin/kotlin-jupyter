package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.Execution
import org.jetbrains.kotlinx.jupyter.api.GenerativeTypeHandler
import org.jetbrains.kotlinx.jupyter.api.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.RendererTypeHandler

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
