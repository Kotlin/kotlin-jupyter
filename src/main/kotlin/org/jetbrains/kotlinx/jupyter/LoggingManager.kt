package org.jetbrains.kotlinx.jupyter

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.OutputStreamAppender
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory

/**
 * This class controls the underlying behavior of how logging works while a notebook is running.
 * It is controlled through magic commands.
 *
 * @see org.jetbrains.kotlinx.jupyter.common.ReplLineMagic.LOG_LEVEL
 * @see org.jetbrains.kotlinx.jupyter.magics.FullMagicsHandler.handleLogLevel
 * @see org.jetbrains.kotlinx.jupyter.common.ReplLineMagic.LOG_HANDLER
 * @see org.jetbrains.kotlinx.jupyter.magics.FullMagicsHandler.handleLogHandler
 */
class LoggingManager(
    loggerFactory: KernelLoggerFactory,
) {
    // Changes to the log level of the root logger will be applied to all child loggers, unless
    // the log level has been set explicitly on them. This does normally not happen when users
    // are interacting with a notebook, but it might happen during tests.
    private val rootLogger = loggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as? Logger

    private val loggerContext
        get() = rootLogger?.loggerContext

    private val basicEncoder =
        run {
            if (loggerContext == null) return@run null
            val encoder = PatternLayoutEncoder()
            encoder.context = loggerContext
            encoder.pattern = "%-4relative [%thread] %-5level %logger{35} - %msg %n"
            encoder.start()
            encoder
        }

    fun setRootLoggingLevel(level: Level) {
        rootLogger?.level = level
    }

    fun disableLogging() = setRootLoggingLevel(Level.OFF)

    fun mainLoggerLevel(): Level {
        val mainLogger = rootLogger ?: return Level.DEBUG
        return mainLogger.effectiveLevel
    }

    fun allLogAppenders(): List<Appender<ILoggingEvent>> {
        val mainLogger = rootLogger ?: return emptyList()
        val result = mutableListOf<Appender<ILoggingEvent>>()
        mainLogger.iteratorForAppenders().forEachRemaining { result.add(it) }
        return result
    }

    fun addAppender(
        name: String,
        appender: Appender<ILoggingEvent>,
    ) {
        if (loggerContext == null || basicEncoder == null) return

        appender.name = name
        appender.context = loggerContext
        (appender as? OutputStreamAppender)?.encoder = basicEncoder
        appender.start()
        rootLogger?.addAppender(appender)
    }

    fun removeAppender(appenderName: String) {
        rootLogger?.detachAppender(appenderName)
    }
}
