package org.jetbrains.kotlinx.jupyter.compiler.daemon

import kotlinx.rpc.annotations.Rpc
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerData
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyAnnotation
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyResolutionResult

/**
 * Service for compiler operations.
 * The daemon implements this service, and the kernel calls it.
 */
@Rpc
interface JupyterCompilerDaemonService {
    /** Initialize the compiler, setting its parameters. Should be called once before other operations.*/
    suspend fun initialize(params: CompilerParams): CompilerData

    suspend fun compile(
        snippetId: Int,
        code: String,
        cellId: Int,
        isUserCode: Boolean,
    ): CompileResultRpc

    suspend fun complete(
        code: String,
        id: Int,
        position: SourcePositionRpc,
    ): List<SourceCodeCompletionVariantRpc>

    suspend fun listErrors(
        code: String,
        id: Int,
    ): List<ScriptDiagnosticRpc>

    /** Check if the code is syntactically complete. */
    suspend fun checkComplete(
        code: String,
        snippetId: Int,
    ): Boolean

    /** Get the current compilation classpath. */
    suspend fun getClasspath(): List<String>
}

/**
 * Service that implements callbacks for compiler operations.
 * The kernel implements this service, and the daemon calls it.
 */
@Rpc
interface KernelCallbackService {
    /** Called by the daemon to report its listening port. [JupyterCompilerDaemonService] can be called on this port. */
    suspend fun reportDaemonPort(port: Int)

    /** Called by the daemon to resolve dependencies from @DependsOn/@Repository annotations. */
    suspend fun resolveDependencies(annotations: List<DependencyAnnotation>): DependencyResolutionResult

    /**
     * In case the classpath was recently updated,
     * the daemon can get the updated classpath by calling this function before compilation.
     */
    suspend fun updatedClasspath(): List<String>
}
