package org.jetbrains.kotlinx.jupyter.api

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer

/**
 * Preprocesses the cell code before its execution
 */
interface CodePreprocessor {
    /**
     * Returns `true` if this preprocessor accepts the given [code]
     */
    fun accepts(code: String): Boolean = true

    /**
     * Performs code preprocessing
     */
    fun process(
        code: String,
        host: KotlinKernelHost,
    ): Result

    data class Result(
        val code: Code,
        val libraries: List<LibraryDefinitionProducer> = emptyList(),
    )
}
