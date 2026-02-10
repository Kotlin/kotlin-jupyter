package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryReference
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable

interface LibraryReferenceParser {
    fun parseReferenceWithArgs(string: String): Pair<LibraryReference, List<Variable>>

    fun parseLibraryReference(string: String): LibraryReference
}
