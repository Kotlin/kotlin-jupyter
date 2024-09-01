package org.jetbrains.kotlinx.jupyter.api

import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorSchemeChangedCallback
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionRequest
import org.jetbrains.kotlinx.jupyter.util.DefaultPromptOptions
import java.io.InputStream
import java.io.PrintStream

/**
 * [Notebook] is a main entry point for Kotlin Jupyter API
 */
interface Notebook {
    /**
     * Represents the execution host for the Kotlin kernel.
     * Could be `null` outside of the execution, so use it with care.
     */
    val executionHost: KotlinKernelHost?

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
     * Kernel Run mode. Determines some capabilities of a running kernel depending on the
     * environment it's running under
     */
    val kernelRunMode: KernelRunMode

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

    /**
     * [LibraryLoader] of the current session
     */
    val libraryLoader: LibraryLoader

    /**
     * Converts descriptor JSON text into [LibraryDefinition]
     */
    fun getLibraryFromDescriptor(
        descriptorText: String,
        options: Map<String, String> = emptyMap(),
    ): LibraryDefinition

    /**
     * Manages custom messages
     */
    val commManager: CommManager

    /**
     * Logger factory of the current session
     */
    val loggerFactory: KernelLoggerFactory

    /**
     * Prompts the user for input and returns the entered value as a String.
     *
     * @param prompt The message displayed to the user as a prompt.
     * @param isPassword A flag indicating whether the input should be treated as a password.
     * Default value is false.
     * Clients usually hide passwords from users by displaying the asterisks ("*") instead of the actual password.
     * @return The user-entered input as a String.
     */
    fun prompt(
        prompt: String = DefaultPromptOptions.PROMPT,
        isPassword: Boolean = DefaultPromptOptions.IS_PASSWORD,
    ): String

    /**
     * The standard output stream that is redirected to the current cell output
     */
    val stdout: PrintStream

    /**
     * The standard error stream that is redirected to the current cell output
     */
    val stderr: PrintStream

    /**
     * The standard input stream that requests data in the context of the current cell
     */
    val stdin: InputStream
}
