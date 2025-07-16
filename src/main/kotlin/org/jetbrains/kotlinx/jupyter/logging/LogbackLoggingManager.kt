package org.jetbrains.kotlinx.jupyter.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.OutputStreamAppender
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.repl.logging.LoggingManager
import org.slf4j.Logger

private val slf4jToLogbackLevel =
    mapOf(
        org.slf4j.event.Level.TRACE to Level.TRACE,
        org.slf4j.event.Level.DEBUG to Level.DEBUG,
        org.slf4j.event.Level.INFO to Level.INFO,
        org.slf4j.event.Level.WARN to Level.WARN,
        org.slf4j.event.Level.ERROR to Level.ERROR,
    )

private val logbackToSLF4JLevel =
    mapOf(
        @Suppress("DEPRECATION")
        Level.ALL
            to org.slf4j.event.Level.TRACE,
        Level.TRACE to org.slf4j.event.Level.TRACE,
        Level.DEBUG to org.slf4j.event.Level.DEBUG,
        Level.INFO to org.slf4j.event.Level.INFO,
        Level.WARN to org.slf4j.event.Level.WARN,
        Level.ERROR to org.slf4j.event.Level.ERROR,
        Level.OFF to org.slf4j.event.Level.ERROR,
    )

class LogbackLoggingManager(
    loggerFactory: KernelLoggerFactory,
) : LoggingManager {
    private val rootLogger = loggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? ch.qos.logback.classic.Logger

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

    override fun setRootLoggingLevel(level: org.slf4j.event.Level) {
        val logbackLevel = slf4jToLogbackLevel[level] ?: return
        setRootLoggingLevel(logbackLevel)
    }

    override fun disableLogging() = setRootLoggingLevel(Level.OFF)

    override fun isLoggingEnabled(): Boolean = mainLogbackLoggerLevel() != Level.OFF

    override fun mainLoggerLevel(): org.slf4j.event.Level {
        val logbackLevel = mainLogbackLoggerLevel()
        return logbackToSLF4JLevel[logbackLevel] ?: org.slf4j.event.Level.DEBUG
    }

    private fun mainLogbackLoggerLevel(): Level {
        val mainLogger = rootLogger ?: return Level.DEBUG
        return mainLogger.level
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
