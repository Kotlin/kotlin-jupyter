package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.AfterCellExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.ClassAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.CodePreprocessor
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.FieldHandler
import org.jetbrains.kotlinx.jupyter.api.FileAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.InternalVariablesMarker
import org.jetbrains.kotlinx.jupyter.api.InterruptionCallback
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.RendererFieldHandler
import org.jetbrains.kotlinx.jupyter.api.TextRendererWithPriority
import org.jetbrains.kotlinx.jupyter.api.ThrowableRenderer
import org.jetbrains.kotlinx.jupyter.util.AcceptanceRule

/**
 * Trivial implementation of [LibraryDefinition] - simple container.
 */
class LibraryDefinitionImpl private constructor() : LibraryDefinition {
    override var options: Map<String, String> = emptyMap()
    override var description: String? = null
    override var website: String? = null
    override var dependencies: List<String> = emptyList()
    override var repositories: List<KernelRepository> = emptyList()
    override var imports: List<String> = emptyList()
    override var init: List<ExecutionCallback<*>> = emptyList()
    override var initCell: List<ExecutionCallback<*>> = emptyList()
    override var afterCellExecution: List<AfterCellExecutionCallback> = emptyList()
    override var shutdown: List<ExecutionCallback<*>> = emptyList()
    override var renderers: List<RendererFieldHandler> = emptyList()
    override var textRenderers: List<TextRendererWithPriority> = emptyList()
    override var throwableRenderers: List<ThrowableRenderer> = emptyList()
    override var converters: List<FieldHandler> = emptyList()
    override var classAnnotations: List<ClassAnnotationHandler> = emptyList()
    override var fileAnnotations: List<FileAnnotationHandler> = emptyList()
    override var resources: List<LibraryResource> = emptyList()
    override var codePreprocessors: List<CodePreprocessor> = emptyList()
    override var internalVariablesMarkers: List<InternalVariablesMarker> = emptyList()
    override var minKernelVersion: KotlinKernelVersion? = null
    override var originalDescriptorText: String? = null
    override var integrationTypeNameRules: List<AcceptanceRule<String>> = emptyList()
    override var interruptionCallbacks: List<InterruptionCallback> = emptyList()
    override var colorSchemeChangedCallbacks: List<ColorSchemeChangedCallback> = emptyList()

    companion object {
        internal fun build(buildAction: (LibraryDefinitionImpl) -> Unit): LibraryDefinition {
            return LibraryDefinitionImpl().also(buildAction)
        }
    }
}

/**
 * Builds an instance of [LibraryDefinition].
 * Build action receives [LibraryDefinitionImpl] as an explicit argument
 * because of problems with names clashing that may arise.
 */
fun libraryDefinition(buildAction: (LibraryDefinitionImpl) -> Unit): LibraryDefinition {
    return LibraryDefinitionImpl.build(buildAction)
}
