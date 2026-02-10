package org.jetbrains.kotlinx.jupyter.compiler.daemon

import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerServiceProvider
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks

/**
 * Daemon-based compiler service provider.
 * This provider has lower priority (10) than in-process (100) and is preferred in production
 * to isolate compiler dependencies from the kernel process.
 *
 * The daemon process runs the actual compiler (from kernel-compiler-impl).
 * This module only contains the client that communicates with the daemon.
 */
class DaemonCompilerServiceProvider : CompilerServiceProvider {
    override val priority: Int = 10

    override fun createCompiler(
        params: CompilerParams,
        callbacks: KernelCallbacks,
    ): CompilerService = DaemonCompilerClient(params, callbacks)
}
