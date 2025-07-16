package org.jetbrains.kotlinx.jupyter.testkit

import org.jetbrains.kotlinx.jupyter.magics.Slf4jLoggingManager
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.repl.logging.LoggingManager
import org.jetbrains.kotlinx.jupyter.repl.logging.LoggingManagerProvider

class TestKitLoggingManagerProvider : LoggingManagerProvider {
    // Should be higher than the default logging manager, which is 0
    override val priority get() = 100

    override fun createLoggingManager(loggerFactory: KernelLoggerFactory): LoggingManager = Slf4jLoggingManager
}
