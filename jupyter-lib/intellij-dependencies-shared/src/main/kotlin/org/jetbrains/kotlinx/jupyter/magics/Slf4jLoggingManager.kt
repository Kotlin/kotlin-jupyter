package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.repl.logging.LoggingManager
import org.slf4j.event.Level

object Slf4jLoggingManager : LoggingManager {
    override fun setRootLoggingLevel(level: Level) {
    }

    override fun mainLoggerLevel(): Level = Level.DEBUG

    override fun disableLogging() {
    }

    override fun isLoggingEnabled(): Boolean = true
}
