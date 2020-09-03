package org.jetbrains.kotlin.jupyter.api

/**
 * Single evaluated notebook cell representation
 */
interface CodeCell {
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
     * Previously evaluated cell
     */
    val prevCell: CodeCell?
}

interface KotlinKernelHost {
    fun display(value: Any)

    fun updateDisplay(value: Any, id: String? = null)

    fun scheduleExecution(code: Code)

    fun execute(code: Code): Any?

    fun executeInit(codes: List<Code>)
}

interface ResultsAccessor {
    operator fun get(i: Int): Any?
}

interface RuntimeUtils: JavaVersionHelper

interface Notebook<CellT: CodeCell> {
    val cells: Map<Int, CellT>
    val results: ResultsAccessor
    val host: KotlinKernelHost

    fun prevCell(before: Int): CellT?
    val thisCell: CellT?
        get() = prevCell(0)
    val lastCell: CellT?
        get() = prevCell(1)

    val kernelVersion: KotlinKernelVersion
    val runtimeUtils: RuntimeUtils
}
