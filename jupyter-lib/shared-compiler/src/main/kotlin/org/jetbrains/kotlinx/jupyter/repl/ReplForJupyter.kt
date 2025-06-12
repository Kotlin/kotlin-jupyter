package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.KernelRunMode
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.api.ThrowableRenderersProcessor
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.outputs.DisplayHandler
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptorsProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.magics.CompoundCodePreprocessor
import org.jetbrains.kotlinx.jupyter.repl.logging.LoggingManager
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import org.jetbrains.kotlinx.jupyter.repl.result.EvalResultEx
import java.io.File

/**
 * This interface represents the main entry point for the Kotlin Jupyter Kernel to interact with the
 * Kotlin Compiler to compile user snippets (cells).
 *
 * Some things to be aware of with regard to terminology:
 *  - Evaluation/Eval: This has different meanings in the Kernel vs. the Compiler. In the Kernel it means both compiling
 *    and running code, but in the Compiler it only means running the compiled code.
 *  - Snippet: In the Compiler it only means Kotlin code, in the Kernel it is used interchangeably to both mean cell
 *    content and kotlin code.
 *  - "Kotlin Kernel", "Kotlin Jupyter Kernel", and "Kernel" are all used interchangeably.
 *
 *
 * The workflow from sending a notebook cell to the Kotlin kernel and getting a response back is complicated, but an
 * overview of the process is given below:
 *
 * 1.  The cell code is wrapped in a [EvalRequestData] and passed to [evalEx].
 * 2.  All library extensions registered through [LibraryDefinition.initCell] are run.
 *     These cannot modify the cell code directly, but can change the classpath or inject new global variables.
 * 3.  The new cell is added to notebook metadata, and the snippet is sent to
 *     `org.jetbrains.kotlinx.jupyter.repl.impl.CellExecutorImpl.execute` to be compiled.
 * 4.  The full cell is sent through all registered processors in [CompoundCodePreprocessor]. In particular, magics
 *     are detected and handled at this step. If this results in a new kernel library being added, e.g., by using
 *     `%use dataframe`, then this library is registered and initialized. This might result in new classes being added
 *     to the classpath. See `org.jetbrains.kotlinx.jupyter.repl.impl.CellExecutorImpl.ExecutionContext.doAddLibraries`.
 * 5.  Any remaining code is now pure Kotlin code. This is sent to [InternalEvaluator.eval]. This class is responsible
 *     for first compiling the snippet, and then executing the compiled code (evaluating it). The result of the
 *     evaluation is returned as an `org.jetbrains.kotlinx.jupyter.repl.result.InternalEvalResult`.
 * 6.  All library extensions registered through [LibraryDefinition.converters] are run on the evaluation context. This
 *     can override the return value from the script.
 * 7.  All library extensions registered through [LibraryDefinition.classAnnotations] are run on the output class.
 *     Like `@Schema` from DataFrames.
 * 8.  [LibrariesScanner] run on the classloader in order to detect if any new kernel libraries have been added. If any
 *     are found they are instantiated. No kernel libraries can be added in step 5-7.
 * 9.  If the color scheme changed any library extensions registered through [LibraryDefinition.colorSchemeChangedCallbacks]
 *     are triggered.
 * 10. Finally, any library extensions registered through [LibraryDefinition.afterCellExecution] are run.
 * 11. The final result is then sent back to [ReplForJupyter].
 * 12. Metadata (imports, source, class files) for the evaluation are collected and stored in
 *     `org.jetbrains.kotlinx.jupyter.repl.result.InternalMetadata`
 ** 13. Metadata and evaluation results are combined in a `InternalReplResult`.
 * 14. If the result is an error, it is rendered using any registered [ThrowableRenderersProcessor], if the evaluation
 *     succeeded, it is rendered using any registered `org.jetbrains.kotlinx.jupyter.codegen.ResultsRenderersProcessor`.
 * 15. The final output is wrapped in a [EvalResultEx] and returned out of [ReplForJupyter] where it is ready to be
 *     serialized in order to be sent across the wire using the Jupyter protocol.
 *
 * For the Jupyter Protocol itself, a starting point is found in
 * [org.jetbrains.kotlinx.jupyter.messaging.AbstractMessageRequestProcessor.processExecuteRequest]. See this class
 * for more information.
 */
interface ReplForJupyter {
    /**
     * Execute line magics or code in the context of the [org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost].
     * Can be used to modify the state of the kernel before or after evaluating user cells.
     */
    fun <T> eval(execution: ExecutionCallback<T>): T

    /**
     * Evaluate a user cell.
     *
     * @return the result of the evaluation, including formatted output.
     */
    fun evalEx(evalData: EvalRequestData): EvalResultEx

    /**
     * Before shutting down the kernel, this will run all shutdown callbacks registered by libraries.
     *
     * @see [org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition.shutdown]
     */
    fun evalOnShutdown(): List<ShutdownEvalResult>

    fun checkComplete(code: Code): CheckCompletenessResult

    suspend fun complete(
        code: Code,
        cursor: Int,
        callback: (CompletionResult) -> Unit,
    )

    suspend fun listErrors(
        code: Code,
        callback: (ListErrorsResult) -> Unit,
    )

    val homeDir: File?

    val debugPort: Int?

    val options: ReplOptions

    val currentSessionState: EvaluatedSnippetMetadata

    val currentClasspath: Collection<String>

    val currentClassLoader: ClassLoader

    val libraryResolver: LibraryResolver?

    val librariesScanner: LibrariesScanner

    val libraryDescriptorsProvider: LibraryDescriptorsProvider

    val runtimeProperties: ReplRuntimeProperties

    val resolutionInfoProvider: ResolutionInfoProvider

    val throwableRenderersProcessor: ThrowableRenderersProcessor

    val notebook: MutableNotebook

    val displayHandler: DisplayHandler

    val fileExtension: String

    val kernelRunMode: KernelRunMode

    val compilerMode: ReplCompilerMode

    val loggingManager: LoggingManager
}
