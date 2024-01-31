package org.jetbrains.kotlinx.jupyter.repl

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.jupyter.repl.result.Classpath
import org.jetbrains.kotlinx.jupyter.repl.result.InternalMetadata
import org.jetbrains.kotlinx.jupyter.repl.result.InternalMetadataImpl
import org.jetbrains.kotlinx.jupyter.repl.result.SerializedCompiledScriptsData

@Serializable
class EvaluatedSnippetMetadata private constructor(
    val newClasspath: Classpath,
    val newSources: Classpath,
    override val compiledData: SerializedCompiledScriptsData,
    override val newImports: List<String>,
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
