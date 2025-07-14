package org.jetbrains.kotlinx.jupyter.magics.contexts

import org.jetbrains.kotlinx.jupyter.repl.logging.LoggingManager

/**
 * Context interface for handlers that need access to logging functionality.
 * Provides access to the LoggingManager for managing logging levels and appenders.
 */
class LoggingMagicHandlerContext(
    /**
     * Manager for controlling logging configuration.
     */
    val loggingManager: LoggingManager,
) : MagicHandlerContext
