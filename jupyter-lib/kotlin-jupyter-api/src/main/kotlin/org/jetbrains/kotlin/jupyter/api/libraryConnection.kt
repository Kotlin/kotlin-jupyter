package org.jetbrains.kotlin.jupyter.api

import org.jetbrains.kotlin.jupyter.util.replaceVariables

typealias TypeName = String
typealias Code = String

interface VariablesSubstitutionAvailable<out T> {
    fun replaceVariables(mapping: Map<String, String>): T
}

fun interface Execution : VariablesSubstitutionAvailable<Execution> {
    fun execute(host: KotlinKernelHost): Any?

    override fun replaceVariables(mapping: Map<String, String>): Execution = this
}

class CodeExecution(val code: Code) : Execution {
    override fun execute(host: KotlinKernelHost): Any? {
        return host.execute(code)
    }

    override fun replaceVariables(mapping: Map<String, String>): CodeExecution {
        return CodeExecution(replaceVariables(code, mapping))
    }
}

interface LibraryDefinition {
    /**
     * List of artifact dependencies in gradle colon-separated format
     */
    val dependencies: List<String>
        get() = emptyList()

    /**
     * List of repository URLs to resolve dependencies in
     */
    val repositories: List<String>
        get() = emptyList()

    /**
     * List of imports: simple and star imports are both allowed
     */
    val imports: List<String>
        get() = emptyList()

    /**
     * List of code snippets evaluated on library initialization
     */
    val init: List<Execution>
        get() = emptyList()

    /**
     * List of code snippets evaluated before every cell evaluation
     */
    val initCell: List<Execution>
        get() = emptyList()

    /**
     * List of code snippets evaluated on kernel shutdown
     */
    val shutdown: List<Execution>
        get() = emptyList()

    val renderers: List<RendererTypeHandler>
        get() = emptyList()
    val converters: List<GenerativeTypeHandler>
        get() = emptyList()
    val annotations: List<GenerativeTypeHandler>
        get() = emptyList()
}

interface LibraryDefinitionProducer {
    fun getDefinitions(notebook: Notebook<*>?): List<LibraryDefinition>
}
