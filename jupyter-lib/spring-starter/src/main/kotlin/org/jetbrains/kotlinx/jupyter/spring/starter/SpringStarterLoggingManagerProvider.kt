package org.jetbrains.kotlinx.jupyter.spring.starter

import org.jetbrains.kotlinx.jupyter.magics.Slf4jLoggingManager
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.repl.logging.LoggingManager
import org.jetbrains.kotlinx.jupyter.repl.logging.LoggingManagerProvider

class SpringStarterLoggingManagerProvider : LoggingManagerProvider {
    override val priority: Int
        get() = 50

    override fun createLoggingManager(loggerFactory: KernelLoggerFactory): LoggingManager = Slf4jLoggingManager
}
