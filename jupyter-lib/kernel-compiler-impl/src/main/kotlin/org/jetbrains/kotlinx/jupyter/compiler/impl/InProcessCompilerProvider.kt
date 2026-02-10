package org.jetbrains.kotlinx.jupyter.compiler.impl

import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerServiceProvider
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks

/**
 * Provider for in-process compiler implementation.
 * This provider has high priority (100) and is used primarily for tests
 * and when out-of-process compilation is not available.
 */
class InProcessCompilerProvider : CompilerServiceProvider {
    override val priority: Int = 100

    override fun createCompiler(
        params: CompilerParams,
        callbacks: KernelCallbacks,
    ): CompilerService = CompilerServiceImpl(params, callbacks)
}
