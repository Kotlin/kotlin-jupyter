package org.jetbrains.kotlinx.jupyter.repl

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.jupyter.repl.result.Classpath
import org.jetbrains.kotlinx.jupyter.repl.result.InternalMetadata
import org.jetbrains.kotlinx.jupyter.repl.result.InternalMetadataImpl
import org.jetbrains.kotlinx.jupyter.repl.result.SerializedCompiledScriptsData

/**
 * This class contains the changes to the compiler environment that has happened as a result of
 * evaluating a single notebook cell.
 *
 * This commonly happens due to adding libraries through the `%use` magics command.
 */
@Serializable
class EvaluatedSnippetMetadata private constructor(
    // The full path on disk to any new dependencies that was added to the REPL classpath.
    val newClasspath: Classpath,
    // The full path on disk to the sources of any new dependencies. I.e., JAR files with the
    // `-source` classifier.
    val newSources: Classpath,
    // Serialized versions of cell code source and the compiled output.
    override val compiledData: SerializedCompiledScriptsData,
    // Any new default imports used for all following cells.
    override val newImports: List<String>,
    // A map of simple variable names to a full qualified definition
    // Example: "x" to "val Line_0_jupyter.x: kotlin.Int"
    val evaluatedVariablesState: Map<String, String?>,
) : InternalMetadata {
    constructor(
        newClasspath: Classpath = emptyList(),
        newSources: Classpath = emptyList(),
        internalMetadata: InternalMetadata = InternalMetadataImpl(),
        evaluatedVariablesState: Map<String, String?> = mutableMapOf(),
    ) :
        this(
            newClasspath,
            newSources,
            internalMetadata.compiledData,
            internalMetadata.newImports,
            evaluatedVariablesState,
        )

    companion object {
        val EMPTY = EvaluatedSnippetMetadata()
    }
}
