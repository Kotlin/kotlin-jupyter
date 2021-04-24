package org.jetbrains.kotlinx.jupyter

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.OutputStreamAppender
import org.slf4j.LoggerFactory

object LoggingManagement {
    private val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as? Logger

    private val loggerContext
        get() = rootLogger?.loggerContext

    private val basicEncoder = run {
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

    fun addAppender(name: String, appender: Appender<ILoggingEvent>) {
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
