package org.jetbrains.kotlinx.jupyter.repl.logging

import org.slf4j.event.Level

/**
 * Bridge interface that allows managing logging settings for the kernel.
 * By "logging" we mean kernel logs that appear in the console
 * or Kotlin Notebook tool window.
 */
interface LoggingManager {
    fun setRootLoggingLevel(level: Level)

    fun mainLoggerLevel(): Level

    fun disableLogging()

    fun isLoggingEnabled(): Boolean
}
