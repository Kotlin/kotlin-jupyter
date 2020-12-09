package org.jetbrains.kotlin.jupyter

import AnnotationsProcessor
import org.jetbrains.kotlin.jupyter.api.Code
import org.jetbrains.kotlin.jupyter.api.CodeExecution
import org.jetbrains.kotlin.jupyter.api.Execution
import org.jetbrains.kotlin.jupyter.api.GenerativeTypeHandler
import org.jetbrains.kotlin.jupyter.api.LibraryDefinition
import org.jetbrains.kotlin.jupyter.api.RendererTypeHandler
import org.jetbrains.kotlin.jupyter.codegen.TypeProvidersProcessor
import org.jetbrains.kotlin.jupyter.libraries.buildDependenciesInitCode

class PreprocessingResultBuilder(
    private val code: Code,
    private val typeProvidersProcessor: TypeProvidersProcessor,
    private val annotationsProcessor: AnnotationsProcessor,
    private val preprocessCode: (Code) -> PreprocessingResult
) {
    private val initCodes = mutableListOf<Execution>()
    private val shutdownCodes = mutableListOf<Execution>()
    private val initCellCodes = mutableListOf<Execution>()
    private val typeRenderers = mutableListOf<RendererTypeHandler>()
    private val typeConverters = mutableListOf<GenerativeTypeHandler>()
    private val annotations = mutableListOf<GenerativeTypeHandler>()

    fun add(libraryDefinition: LibraryDefinition) {
        libraryDefinition.buildDependenciesInitCode()?.let { initCodes.add(CodeExecution(it)) }
        typeRenderers.addAll(libraryDefinition.renderers)
        typeConverters.addAll(libraryDefinition.converters)
        annotations.addAll(libraryDefinition.annotations)
        initCellCodes.addAll(libraryDefinition.initCell)
        shutdownCodes.addAll(libraryDefinition.shutdown)
        libraryDefinition.init.forEach {
            if (it is CodeExecution) {
                // Library init code may contain other magics, so we process them recursively
                val preprocessed = preprocessCode(it.code)
                initCodes.addAll(preprocessed.initCodes)
                typeRenderers.addAll(preprocessed.typeRenderers)
                initCellCodes.addAll(preprocessed.initCellCodes)
                shutdownCodes.addAll(preprocessed.shutdownCodes)
                if (preprocessed.code.isNotBlank()) {
                    initCodes.add(CodeExecution(preprocessed.code))
                }
            } else {
                initCodes.add(it)
            }
        }
    }

    fun build(): PreprocessingResult {
        val declarations = (typeConverters.map { typeProvidersProcessor.register(it) } + annotations.map { annotationsProcessor.register(it) })
            .joinToString("\n")
        if (declarations.isNotBlank()) {
            initCodes.add(CodeExecution(declarations))
        }

        return PreprocessingResult(code, initCodes, shutdownCodes, initCellCodes, typeRenderers)
    }
}

data class PreprocessingResult(
    val code: Code,
    val initCodes: List<Execution>,
    val shutdownCodes: List<Execution>,
    val initCellCodes: List<Execution>,
    val typeRenderers: List<RendererTypeHandler>,
)
