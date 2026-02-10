package org.jetbrains.kotlinx.jupyter.compiler.api

import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory

/**
 * SPI interface for loading compiler service implementations.
 * Supports both in-process (test) and out-of-process (daemon) compilation.
 */
interface CompilerServiceProvider {
    /**
     * Priority for SPI loading. Higher priority providers are preferred.
     * Test implementations should return a higher priority (e.g., 100).
     * Production daemon should return a lower priority (e.g., 10).
     */
    val priority: Int

    /**
     * Create a compiler service instance.
     */
    fun createCompiler(
        params: CompilerParams,
        callbacks: KernelCallbacks,
        loggerFactory: KernelLoggerFactory,
    ): CompilerService
}
