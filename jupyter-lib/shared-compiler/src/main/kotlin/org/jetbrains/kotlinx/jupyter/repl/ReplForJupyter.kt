package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.codegen.ThrowableRenderersProcessor
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptorsProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import java.io.File

interface ReplForJupyter {

    fun <T> eval(execution: ExecutionCallback<T>): T

    fun evalEx(evalData: EvalRequestData): EvalResultEx

    fun evalOnShutdown(): List<ShutdownEvalResult>

    fun checkComplete(code: Code): CheckCompletenessResult

    suspend fun complete(code: Code, cursor: Int, callback: (CompletionResult) -> Unit)

    suspend fun listErrors(code: Code, callback: (ListErrorsResult) -> Unit)

    val homeDir: File?

    val debugPort: Int?

    val options: ReplOptions

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

    val isEmbedded: Boolean
        get() = false
}
