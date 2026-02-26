package org.jetbrains.kotlinx.jupyter.compiler.impl

import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerServiceProvider
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory

/**
 * In-process compiler service provider.
 * This provider has lower priority (100) and is used as a fallback
 * when the daemon provider is not available, or for testing.
 */
class InProcessCompilerServiceProvider : CompilerServiceProvider {
    override val priority: Int = 100

    override fun createCompiler(
        params: CompilerParams,
        callbacks: KernelCallbacks,
        loggerFactory: KernelLoggerFactory,
    ): CompilerService {
        return CompilerServiceImpl(params, callbacks, loggerFactory)
    }
}
