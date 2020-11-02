package org.jetbrains.kotlin.jupyter.api

/**
 * Single evaluated notebook cell representation
 */
interface CodeCell {
    /**
     * Reference to the notebook instance
     */
    val notebook: Notebook<*>

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
}

/**
 * Interface representing kernel engine, the core facility for compiling and executing code snippets
 */
interface KotlinKernelHost {
    /**
     * Try to display the given value. It is only displayed if it's an instance of [Renderable]
     * or may be converted to it
     */
    fun display(value: Any)

    /**
     * Updates display data with given [id] with the new [value]
     */
    fun updateDisplay(value: Any, id: String? = null)

    /**
     * Schedules execution of the given [execution] after the completing of execution of the current cell
     */
    fun scheduleExecution(execution: Execution)

    /**
     * See [scheduleExecution]
     */
    fun scheduleExecution(execution: Code) = scheduleExecution(CodeExecution(execution))

    /**
     * Executes code immediately. Note that it may lead to breaking the kernel state in some cases
     */
    fun execute(code: Code): Any?

    fun executeInit(codes: List<Code>)

    fun executeInternal(code: Code): Result

    /**
     * Adds a new library via its definition. Fully interchangeable with `%use` approach
     */
    fun addLibrary(definition: LibraryDefinition)

    class Result(val value: Any?, val fieldName: String?)
}

interface ResultsAccessor {
    operator fun get(i: Int): Any?
}

interface RuntimeUtils: JavaVersionHelper

interface Notebook<CellT: CodeCell> {
    val cells: Map<Int, CellT>
    val results: ResultsAccessor
    val displays: DisplayContainer
    val host: KotlinKernelHost

    fun history(before: Int): CellT?
    val currentCell: CellT?
        get() = history(0)
    val lastCell: CellT?
        get() = history(1)

    val kernelVersion: KotlinKernelVersion
    val runtimeUtils: RuntimeUtils
}
