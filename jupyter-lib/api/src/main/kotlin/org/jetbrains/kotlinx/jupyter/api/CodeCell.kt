package org.jetbrains.kotlinx.jupyter.api

/**
 * Single evaluated notebook cell representation
 */
interface CodeCell {
    /**
     * Reference to the notebook instance
     */
    val notebook: Notebook

    /**
     * Displayed cell ID
     */
    val id: Int

    /**
     * Internal cell ID which is used to generate internal class names and result fields
     */
    val internalId: Int

    /**
     * Cell code
     */
    val code: String

    /**
     * Cell code after magic preprocessing
     */
    val preprocessedCode: String

    /**
     * Cell result value
     */
    val result: Any?

    /**
     * Cell standard output
     */
    val streamOutput: String

    /**
     * Cell displays
     */
    val displays: DisplayContainer

    /**
     * Previously evaluated cell
     */
    val prevCell: CodeCell?

    /**
     * Ordered list of snippet declarations
     */
    val declarations: List<DeclarationInfo>
}
