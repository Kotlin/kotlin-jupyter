package org.jetbrains.kotlinx.jupyter.magics

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.FileAppender
import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.logging.LogbackLoggingManager
import org.jetbrains.kotlinx.jupyter.magics.contexts.CommandHandlingMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.LoggingMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.MagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.requireContext

/**
 * Handler for log appender management commands.
 * Handles the %logHandler command for managing log appenders.
 */
class LogbackLoggingMagicsHandler(
    context: MagicHandlerContext,
) : BasicMagicsHandler(context) {
    private val loggingContext = context.requireContext<LoggingMagicHandlerContext>()

    override val callbackMap: Map<ReplLineMagic, () -> Unit> =
        mapOf(
            ReplLineMagic.LOG_HANDLER to ::handleLogHandler,
        )

    /**
     * Handles the %logHandler command, which manages log appenders.
     */
    private fun handleLogHandler() {
        val logbackLoggingManager = loggingContext.loggingManager as? LogbackLoggingManager ?: return
        val commandArgs = commandHandlingContext.arg?.split(Regex("""\s+""")).orEmpty()
        val command = commandArgs.firstOrNull() ?: throw ReplException("Log handler command has not been passed")

        when (command) {
            "list" -> {
                println("Log appenders:")
                logbackLoggingManager.allLogAppenders().forEach {
                    println(
                        buildString {
                            append(it.name)
                            append(" of type ")
                            append(it::class.simpleName)
                            if (it is FileAppender) {
                                append("(${it.file})")
                            }
                        },
                    )
                }
            }
            "add" -> {
                val appenderName = commandArgs.getOrNull(1) ?: throw ReplException("Log handler add command needs appender name argument")
                val appenderType = commandArgs.getOrNull(2) ?: throw ReplException("Log handler add command needs appender type argument")
                val appenderTypeArgs = commandArgs.subList(3, commandArgs.size)

                val appender: Appender<ILoggingEvent> =
                    when (appenderType) {
                        "--file" -> {
                            val fileName =
                                appenderTypeArgs.getOrNull(
                                    0,
                                ) ?: throw ReplException("File appender needs file name to be specified")
                            val res = FileAppender<ILoggingEvent>()
                            res.file = fileName
                            res
                        }
                        else -> throw ReplException("Unknown appender type: $appenderType")
                    }
                logbackLoggingManager.addAppender(appenderName, appender)
            }
            "remove" -> {
                val appenderName =
                    commandArgs.getOrNull(
                        1,
                    ) ?: throw ReplException("Log handler remove command needs appender name argument")
                logbackLoggingManager.removeAppender(appenderName)
            }
            else -> throw ReplException("Unknown log handler command: $command")
        }
    }

    companion object : MagicHandlerFactoryImpl(
        ::LogbackLoggingMagicsHandler,
        listOf(
            LoggingMagicHandlerContext::class,
            CommandHandlingMagicHandlerContext::class,
        ),
    )
}
