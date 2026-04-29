package org.jetbrains.kotlinx.jupyter.compiler.daemon.client

import kotlinx.coroutines.runBlocking
import kotlinx.rpc.withService
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResult
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerData
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.jetbrains.kotlinx.jupyter.compiler.daemon.JupyterCompilerDaemonService
import org.jetbrains.kotlinx.jupyter.compiler.daemon.fromRpc
import org.jetbrains.kotlinx.jupyter.compiler.daemon.toRpc
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import java.io.Closeable
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.SourceCodeCompletionVariant

/**
 * Client that communicates with the compiler daemon via WebSocket.
 */
class DaemonCompilerClient(
    private val params: CompilerParams,
    callbacks: KernelCallbacks,
    loggerFactory: KernelLoggerFactory,
) : CompilerService,
    Closeable {
    private val logger = loggerFactory.getLogger(DaemonCompilerClient::class.java)

    private val processHandler = RetryingDaemonProcessHandler(callbacks, loggerFactory)

    private val compilerService: JupyterCompilerDaemonService = processHandler.rpcClient.withService()
    private val compilerData =
        runBlocking {
            compilerService.initialize(params).also {
                logger.debug("Compiler daemon initialized successfully")
            }
        }

    override fun close() {
        processHandler.close()
    }

    override suspend fun getCompilerData(): CompilerData = compilerData

    override suspend fun compile(
        snippetId: Int,
        code: String,
        cellId: Int,
        isUserCode: Boolean,
        cachedScriptHashCodes: List<Int>,
    ): CompileResult = compilerService.compile(snippetId, code, cellId, isUserCode, cachedScriptHashCodes).fromRpc()

    override suspend fun complete(
        code: String,
        id: Int,
        position: SourceCode.Position,
    ): List<SourceCodeCompletionVariant> = compilerService.complete(code, id, position.toRpc()).map { it.fromRpc() }

    override suspend fun listErrors(
        code: String,
        id: Int,
    ): List<ScriptDiagnostic> = compilerService.listErrors(code, id).map { it.fromRpc() }

    override suspend fun checkComplete(
        code: String,
        snippetId: Int,
    ): Boolean = compilerService.checkComplete(code, snippetId)

    override suspend fun getClasspath(): List<String> = compilerService.getClasspath()
}
