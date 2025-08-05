package org.jetbrains.kotlinx.jupyter.api.libraries

class LibraryResolutionRequest(
    val reference: LibraryReference,
    val arguments: List<Variable>,
    val definition: LibraryDefinition,
) {
    override fun toString(): String =
        buildString {
            append("Library request: ")
            append(reference.toString())
            if (arguments.isNotEmpty()) {
                append("(")
                append(arguments.map { "${it.name} = ${it.value}" })
                append(")")
            }
        }
}
