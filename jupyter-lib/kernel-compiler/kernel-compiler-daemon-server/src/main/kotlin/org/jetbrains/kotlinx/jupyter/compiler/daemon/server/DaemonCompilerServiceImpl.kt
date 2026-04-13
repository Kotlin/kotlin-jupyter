package org.jetbrains.kotlinx.jupyter.compiler.daemon.server

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyAnnotation
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyResolutionResult
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.jetbrains.kotlinx.jupyter.compiler.daemon.CompileResultRpc
import org.jetbrains.kotlinx.jupyter.compiler.daemon.JupyterCompilerDaemonService
import org.jetbrains.kotlinx.jupyter.compiler.daemon.KernelCallbackService
import org.jetbrains.kotlinx.jupyter.compiler.daemon.ScriptDiagnosticRpc
import org.jetbrains.kotlinx.jupyter.compiler.daemon.SourceCodeCompletionVariantRpc
import org.jetbrains.kotlinx.jupyter.compiler.daemon.SourcePositionRpc
import org.jetbrains.kotlinx.jupyter.compiler.daemon.fromRpc
import org.jetbrains.kotlinx.jupyter.compiler.daemon.toRpc
import org.jetbrains.kotlinx.jupyter.compiler.impl.CompilerServiceImpl
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import java.io.Closeable
import java.util.concurrent.Executors

/**
 * Service implementation for the compiler daemon.
 * Implements the JupyterCompilerDaemonService interface.
 * This runs inside the daemon process.
 */
class DaemonCompilerServiceImpl(
    private val callbackService: KernelCallbackService,
) : JupyterCompilerDaemonService,
    Closeable {
    private var compiler: CompilerServiceImpl? = null

    // Single-threaded dispatcher for all compiler operations to work around KT-73200
    // The Kotlin compiler has thread-safety issues when accessed from multiple threads
    private val compilerDispatcher: CoroutineDispatcher =
        Executors
            .newSingleThreadExecutor { runnable ->
                Thread(runnable, "compiler-thread")
            }.asCoroutineDispatcher()

    override suspend fun initialize(params: CompilerParams) =
        withContext(compilerDispatcher) {
            val callbacks = DaemonKernelCallbacks(callbackService)
            compiler = CompilerServiceImpl(params, callbacks, DefaultKernelLoggerFactory)
            compiler!!.getCompilerData()
        }

    override suspend fun compile(
        snippetId: Int,
        code: String,
        cellId: Int,
        isUserCode: Boolean,
    ): CompileResultRpc =
        withContext(compilerDispatcher) {
            val currentCompiler =
                compiler
                    ?: throw IllegalStateException("Compiler not initialized")

            currentCompiler
                .compile(
                    snippetId = snippetId,
                    code = code,
                    cellId = cellId,
                    isUserCode = isUserCode,
                ).toRpc()
        }

    override suspend fun complete(
        code: String,
        id: Int,
        position: SourcePositionRpc,
    ): List<SourceCodeCompletionVariantRpc> =
        withContext(compilerDispatcher) {
            val currentCompiler =
                compiler
                    ?: throw IllegalStateException("Compiler not initialized")

            currentCompiler.complete(code, id, position.fromRpc()).map { it.toRpc() }
        }

    override suspend fun listErrors(
        code: String,
        id: Int,
    ): List<ScriptDiagnosticRpc> =
        withContext(compilerDispatcher) {
            val currentCompiler =
                compiler
                    ?: throw IllegalStateException("Compiler not initialized")

            currentCompiler.listErrors(code, id).map { it.toRpc() }
        }

    override suspend fun checkComplete(
        code: String,
        snippetId: Int,
    ): Boolean =
        withContext(compilerDispatcher) {
            val currentCompiler =
                compiler
                    ?: throw IllegalStateException("Compiler not initialized")

            currentCompiler.checkComplete(code, snippetId)
        }

    override suspend fun getClasspath(): List<String> =
        withContext(compilerDispatcher) {
            val currentCompiler =
                compiler
                    ?: throw IllegalStateException("Compiler not initialized")

            currentCompiler.getClasspath()
        }

    override fun close() {
        compiler?.close()
    }
}

/**
 * Implements KernelCallbacks by making calls back to the kernel.
 */
private class DaemonKernelCallbacks(
    private val callbackService: KernelCallbackService,
) : KernelCallbacks {
    override suspend fun resolveDependencies(annotations: List<DependencyAnnotation>): DependencyResolutionResult =
        callbackService.resolveDependencies(annotations)

    override suspend fun updatedClasspath(): List<String> = callbackService.updatedClasspath()
}
