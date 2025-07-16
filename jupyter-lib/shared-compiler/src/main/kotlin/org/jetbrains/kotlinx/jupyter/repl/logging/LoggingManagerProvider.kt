package org.jetbrains.kotlinx.jupyter.repl.logging

import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory

/**
 * Creates [LoggingManager] instances.
 * It's an SPI interface to provide custom [LoggingManager]
 * implementation to the REPL.
 * The implementation with the highest priority is used.
 */
interface LoggingManagerProvider {
    val priority: Int

    fun createLoggingManager(loggerFactory: KernelLoggerFactory): LoggingManager
}
