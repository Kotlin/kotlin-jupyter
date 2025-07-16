package org.jetbrains.kotlinx.jupyter.logging

import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.repl.logging.LoggingManager
import org.jetbrains.kotlinx.jupyter.repl.logging.LoggingManagerProvider

class LogbackLoggingManagerProvider : LoggingManagerProvider {
    override val priority get() = 0

    override fun createLoggingManager(loggerFactory: KernelLoggerFactory): LoggingManager = LogbackLoggingManager(loggerFactory)
}
