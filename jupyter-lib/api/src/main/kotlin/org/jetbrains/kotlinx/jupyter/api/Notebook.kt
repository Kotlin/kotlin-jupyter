package org.jetbrains.kotlinx.jupyter.api

import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorSchemeChangedCallback
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterConnection
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionRequest

/**
 * [Notebook] is a main entry point for Kotlin Jupyter API
 */
interface Notebook {
    /**
     * All executed cells of this notebook
     */
    val cellsList: Collection<CodeCell>

    /**
     * Current state of visible variables
     */
    val variablesState: Map<String, VariableState>

    /**
     * Stores info about useful variables in a cell.
     * Key: cellId;
     * Value: set of variable names.
     * Useful <==> declarations + modifying references
     */
    val cellVariables: Map<Int, Set<String>>

    /**
     * Returns the object that allows to access execution results
     */
    val resultsAccessor: ResultsAccessor

    val displays: DisplayContainer

    /**
     * Mapping allowing to get cell by execution number
     */
    @Throws(IndexOutOfBoundsException::class)
    fun getCell(id: Int): CodeCell

    /**
     * Mapping allowing to get result by execution number
     */
    @Throws(IndexOutOfBoundsException::class)
    fun getResult(id: Int): Any?

    /**
     * Information about all display data objects
     */
    fun getAllDisplays(): List<DisplayResultWithCell>

    /**
     * Information about displays with the given [id]
     */
    fun getDisplaysById(id: String?): List<DisplayResultWithCell>

    /**
     * Get cell by relative offset: 0 for current cell,
     * 1 for previous cell, and so on
     *
     * @param before Relative offset
     * @return Cell from history
     */
    fun history(before: Int): CodeCell?

    /**
     * Current color scheme. Works correctly only in Kotlin Notebook plugin
     */
    val currentColorScheme: ColorScheme?

    /**
     * Change color scheme and run callbacks. Works correctly only in Kotlin Notebook plugin
     */
    fun changeColorScheme(newScheme: ColorScheme)

    /**
     * Renders HTML as iframe that fixes scrolling and color scheme issues in Kotlin Notebook plugin
     */
    fun renderHtmlAsIFrame(data: HtmlData): MimeTypedResult

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

    /**
     * Jupyter client that started the current kernel session
     */
    val jupyterClientType: JupyterClientType

    /**
     * Renderers processor gives an ability to render values and
     * and add new renderers
     */
    val renderersProcessor: RenderersProcessor

    val textRenderersProcessor: TextRenderersProcessor

    val fieldsHandlersProcessor: FieldsProcessor

    val beforeCellExecutionsProcessor: ExtensionsProcessor<ExecutionCallback<*>>
    val afterCellExecutionsProcessor: ExtensionsProcessor<AfterCellExecutionCallback>
    val shutdownExecutionsProcessor: ExtensionsProcessor<ExecutionCallback<*>>

    val codePreprocessorsProcessor: ExtensionsProcessor<CodePreprocessor>
    val interruptionCallbacksProcessor: ExtensionsProcessor<InterruptionCallback>

    val colorSchemeChangeCallbacksProcessor: ExtensionsProcessor<ColorSchemeChangedCallback>

    /**
     * All requests for libraries made during this session
     */
    val libraryRequests: Collection<LibraryResolutionRequest>
    val libraryLoader: LibraryLoader
    fun getLibraryFromDescriptor(descriptorText: String, options: Map<String, String> = emptyMap()): LibraryDefinition

    val connection: JupyterConnection

    val commManager: CommManager
}
