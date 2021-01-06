package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.ExactRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.GenerativeTypeHandler
import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResource
import org.jetbrains.kotlinx.jupyter.util.GenerativeHandlersSerializer
import org.jetbrains.kotlinx.jupyter.util.RenderersSerializer

@Serializable
class LibraryDescriptor(
    val libraryDefinitions: List<Code> = emptyList(),
    override val dependencies: List<String> = emptyList(),

    @Serializable(VariablesSerializer::class)
    @SerialName("properties")
    val variables: List<Variable> = emptyList(),

    override val initCell: List<CodeExecution> = emptyList(),
    override val imports: List<String> = emptyList(),
    override val repositories: List<String> = emptyList(),
    override val init: List<CodeExecution> = emptyList(),
    override val shutdown: List<CodeExecution> = emptyList(),

    @Serializable(RenderersSerializer::class)
    override val renderers: List<ExactRendererTypeHandler> = emptyList(),

    @Serializable(GenerativeHandlersSerializer::class)
    @SerialName("typeConverters")
    override val converters: List<GenerativeTypeHandler> = emptyList(),

    override val resources: List<LibraryResource> = emptyList(),

    val link: String? = null,
    val description: String? = null,
    val minKernelVersion: String? = null,
) : LibraryDefinition
