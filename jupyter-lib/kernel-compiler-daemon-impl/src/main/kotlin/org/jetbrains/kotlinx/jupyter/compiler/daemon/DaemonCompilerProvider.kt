package org.jetbrains.kotlinx.jupyter.compiler.daemon

import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerServiceProvider
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks

/**
 * Provider for daemon-based compiler implementation.
 * This provider has low priority (10) and is used by default
 * for production use to isolate compiler dependencies.
 */
class DaemonCompilerProvider : CompilerServiceProvider {
    override val priority: Int = 10

    override fun createCompiler(
        params: CompilerParams,
        callbacks: KernelCallbacks,
    ): CompilerService = DaemonCompilerClient(params, callbacks)
}
