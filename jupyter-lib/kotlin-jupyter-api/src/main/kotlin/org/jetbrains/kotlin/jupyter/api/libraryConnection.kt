package org.jetbrains.kotlin.jupyter.api

typealias TypeName = String
typealias Code = String

data class TypeHandler(val className: TypeName, val code: Code)

open class LibraryDefinition(
        /**
         * List of artifact dependencies in gradle colon-separated format
         */
        val dependencies: List<String> = emptyList(),

        /**
         * List of repository URLs to resolve dependencies in
         */
        val repositories: List<String> = emptyList(),

        /**
         * List of imports: simple and star imports are both allowed
         */
        val imports: List<String> = emptyList(),

        /**
         * List of code snippets evaluated on library initialization
         */
        val init: List<Code> = emptyList(),

        /**
         * List of code snippets evaluated before every cell evaluation
         */
        val initCell: List<Code> = emptyList(),

        /**
         * List of code snippets evaluated on kernel shutdown
         */
        val shutdown: List<Code> = emptyList(),


        val renderers: List<TypeHandler> = emptyList(),
        val converters: List<TypeHandler> = emptyList(),
        val annotations: List<TypeHandler> = emptyList(),
)

interface LibraryDefinitionProducer {
        fun getDefinitions(notebook: Notebook<*>?): List<LibraryDefinition>
}
