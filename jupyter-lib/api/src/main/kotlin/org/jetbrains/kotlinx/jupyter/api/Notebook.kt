package org.jetbrains.kotlinx.jupyter.api

/**
 * [Notebook] is a main entry point for Kotlin Jupyter API
 */
interface Notebook {
    /**
     * Mapping allowing to get cell by execution number
     */
    val cells: Map<Int, CodeCell>

    /**
     * Mapping allowing to get result by execution number
     */
    val results: ResultsAccessor

    /**
     * Information about all display data objects
     */
    val displays: DisplayContainer

    /**
     * Get cell by relative offset: 0 for current cell,
     * 1 for previous cell, and so on
     *
     * @param before Relative offset
     * @return Cell from history
     */
    fun history(before: Int): CodeCell?

    /**
     * Current cell
     */
    val currentCell: CodeCell?
        get() = history(0)

    /**
     * Last evaluated cell
     */
    val lastCell: CodeCell?
        get() = history(1)

    /**
     * Current kernel version
     */
    val kernelVersion: KotlinKernelVersion

    /**
     * Current JRE info
     */
    val jreInfo: JREInfoProvider
}
