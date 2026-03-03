package org.jetbrains.kotlinx.jupyter.compiler.daemon

import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerServiceProvider
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory

/**
 * Daemon-based compiler service provider.
 * The daemon process runs the actual compiler (from kernel-compiler-impl).
 * This module only contains the client that communicates with the daemon.
 */
class DaemonCompilerServiceProvider : CompilerServiceProvider {
    override val priority: Int = PRIORITY

    override fun createCompiler(
        params: CompilerParams,
        callbacks: KernelCallbacks,
        loggerFactory: KernelLoggerFactory,
    ): CompilerService = DaemonCompilerClient(params, callbacks, loggerFactory)

    companion object {
        const val PRIORITY = 10
    }
}
