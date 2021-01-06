package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.AnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.GenerativeTypeHandler
import org.jetbrains.kotlinx.jupyter.api.RendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.Execution
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResource
import org.jetbrains.kotlinx.jupyter.codegen.AnnotationsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.TypeProvidersProcessor
import org.jetbrains.kotlinx.jupyter.codegen.TypeRenderersProcessor
import org.jetbrains.kotlinx.jupyter.libraries.buildDependenciesInitCode

class PreprocessingResultBuilder(
    private val typeProvidersProcessor: TypeProvidersProcessor,
    private val annotationsProcessor: AnnotationsProcessor,
    private val typeRenderersProcessor: TypeRenderersProcessor,
    private val preprocessCode: (Code, PreprocessingResultBuilder, Boolean) -> Unit
) {
    private val codeBuilder = StringBuilder()
    private val initCodes = mutableListOf<Execution>()
    private val shutdownCodes = mutableListOf<Execution>()
    private val initCellCodes = mutableListOf<Execution>()
    private val typeRenderers = mutableListOf<RendererTypeHandler>()
    private val typeConverters = mutableListOf<GenerativeTypeHandler>()
    private val annotations = mutableListOf<AnnotationHandler>()
    private val resources = mutableListOf<LibraryResource>()

    fun add(libraryDefinition: LibraryDefinition) {
        libraryDefinition.buildDependenciesInitCode()?.let { initCodes.add(CodeExecution(it)) }
        typeRenderers.addAll(libraryDefinition.renderers)
        typeConverters.addAll(libraryDefinition.converters)
        annotations.addAll(libraryDefinition.annotations)
        resources.addAll(libraryDefinition.resources)
        initCellCodes.addAll(libraryDefinition.initCell)
        shutdownCodes.addAll(libraryDefinition.shutdown)
        libraryDefinition.init.forEach {
            if (it is CodeExecution) {
                // Library init code may contain other magics, so we process them recursively
                preprocessCode(it.code, this, true)
            } else {
                initCodes.add(it)
            }
        }
    }

    fun addCode(code: Code, asInitCode: Boolean = false) {
        if (code.isBlank()) return
        if (asInitCode) initCodes.add(CodeExecution(code))
        else codeBuilder.append(code)
    }

    fun build(): PreprocessingResult {
        val declarationsList = mutableListOf<String>()
        typeConverters.mapTo(declarationsList) { typeProvidersProcessor.register(it) }
        annotations.forEach { annotationsProcessor.register(it) }
        typeRenderers.mapNotNullTo(declarationsList) { typeRenderersProcessor.register(it) }
        val declarations = declarationsList.joinToString("\n")
        if (declarations.isNotBlank()) {
            initCodes.add(CodeExecution(declarations))
        }

        return PreprocessingResult(codeBuilder.toString(), initCodes, shutdownCodes, initCellCodes, resources)
    }

    fun clearInitCodes() {
        initCodes.clear()
    }

    fun clear() {
        codeBuilder.clear()
        initCodes.clear()
        shutdownCodes.clear()
        initCellCodes.clear()
        typeRenderers.clear()
        typeConverters.clear()
        annotations.clear()
        resources.clear()
    }
}

data class PreprocessingResult(
    val code: Code,
    val initCodes: List<Execution>,
    val shutdownCodes: List<Execution>,
    val initCellCodes: List<Execution>,
    val resources: List<LibraryResource>,
)
