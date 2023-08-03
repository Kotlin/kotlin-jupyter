package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.FieldsProcessor
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost

/**
 * Processes REPL local variables (implemented as properties of script classes) and converts them into new types using code generation
 */
interface FieldsProcessorInternal : FieldsProcessor {

    /**
     * Processes local variables and generates code snippets that perform type conversions
     */
    fun process(host: KotlinKernelHost): FieldValue?
}
