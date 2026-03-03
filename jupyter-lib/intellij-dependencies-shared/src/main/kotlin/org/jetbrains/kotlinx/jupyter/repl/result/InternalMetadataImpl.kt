package org.jetbrains.kotlinx.jupyter.repl.result

data class InternalMetadataImpl(
    override val compiledData: SerializedCompiledScriptsData = SerializedCompiledScriptsData.EMPTY,
    override val newImports: List<String> = emptyList(),
) : InternalMetadata
