package org.jetbrains.kotlin.jupyter.api

interface CodeCell {
    val id: Int
    val internalId: Int

    val code: String
    val result: Any?
    val streamOutput: String

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
