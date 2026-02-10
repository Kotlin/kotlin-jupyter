package org.jetbrains.kotlinx.jupyter.compiler.daemon

import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerServiceProvider
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks

/**
 * Daemon-based compiler service provider.
 * This provider has higher priority (10) and is preferred in production
 * to isolate compiler dependencies from the kernel process.
 */
class DaemonCompilerServiceProvider : CompilerServiceProvider {
    override val priority: Int = 10

    override fun createCompiler(
        params: CompilerParams,
        callbacks: KernelCallbacks,
    ): CompilerService {
        // TODO: Start daemon process and return DaemonCompilerClient
        // For now, fall back to in-process compilation
        throw UnsupportedOperationException(
            "Daemon compiler not yet implemented. " +
                "The in-process compiler (priority 100) will be used instead.",
        )
    }
}
