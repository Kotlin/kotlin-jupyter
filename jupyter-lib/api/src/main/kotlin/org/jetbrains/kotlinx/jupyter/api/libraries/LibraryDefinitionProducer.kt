package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.Notebook

interface LibraryDefinitionProducer {
    fun getDefinitions(notebook: Notebook<*>?): List<LibraryDefinition>
}
