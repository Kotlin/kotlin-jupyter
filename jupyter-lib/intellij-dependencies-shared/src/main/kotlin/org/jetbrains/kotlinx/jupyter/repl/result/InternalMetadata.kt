package org.jetbrains.kotlinx.jupyter.repl.result

/**
 * This interface represents the changes to the compiler environment that has happened as a result of
 * evaluating one or more notebook cells.
 */
interface InternalMetadata {
    /**
     * A reference to a serialized version of a cell (as text) as well as the compiled snippet class.
     */
    val compiledData: SerializedCompiledScriptsData

    /**
     * Any new default imports created as part of evaluating the cell. These will
     * be used automatically for all following cells.
     */
    val newImports: List<String>
}
