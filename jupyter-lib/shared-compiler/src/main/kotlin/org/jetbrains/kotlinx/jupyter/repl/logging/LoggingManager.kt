package org.jetbrains.kotlinx.jupyter.repl.logging

import org.slf4j.event.Level

interface LoggingManager {
    fun setRootLoggingLevel(level: Level)

    fun mainLoggerLevel(): Level

    fun disableLogging()

    fun isLoggingEnabled(): Boolean
}
