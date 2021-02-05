package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.Notebook

/**
 * [LibraryDefinitionProducer] may produce several library definitions.
 * You may want produce or not produce definitions based on
 * kernel, Kotlin or JRE version, or some other info provided by [Notebook]
 */
interface LibraryDefinitionProducer {
    fun getDefinitions(notebook: Notebook<*>?): List<LibraryDefinition>
}
