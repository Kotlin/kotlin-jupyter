package org.jetbrains.kotlinx.jupyter.compiler

import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerServiceProvider
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import java.util.ServiceLoader

/**
 * Factory for creating CompilerService instances using SPI.
 * Loads all available CompilerServiceProvider implementations and selects
 * the one with the highest priority.
 */
object CompilerServiceFactory {
    /**
     * Creates a CompilerService instance using the highest priority provider.
     * Priority values:
     * - In-process provider (tests): 100
     * - Daemon provider (production): 10
     *
     * @param params Compiler initialization parameters
     * @param callbacks Kernel callback interface for reporting imports, declarations, and dependency resolution
     * @param loggerFactory Factory for creating loggers
     * @return CompilerService instance
     * @throws IllegalStateException if no providers are available
     */
    fun createCompilerService(
        params: CompilerParams,
        callbacks: KernelCallbacks,
        loggerFactory: KernelLoggerFactory,
        compilerServiceSpiClassloader: ClassLoader,
    ): CompilerService {
        val providers = ServiceLoader.load(CompilerServiceProvider::class.java, compilerServiceSpiClassloader).toList()

        if (providers.isEmpty()) {
            throw IllegalStateException(
                "No CompilerServiceProvider implementations found. " +
                    "Ensure kernel-compiler-impl or kernel-compiler-daemon-impl is on the classpath.",
            )
        }

        val selectedProvider =
            providers.maxByOrNull { it.priority }
                ?: error("Failed to select provider from list: $providers")

        return selectedProvider.createCompiler(params, callbacks, loggerFactory)
    }
}
