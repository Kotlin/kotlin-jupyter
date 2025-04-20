package org.jetbrains.kotlinx.jupyter.magics

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.FileAppender
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.libraries.DefaultInfoSwitch
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.logging.LogbackLoggingManager
import org.jetbrains.kotlinx.jupyter.repl.ReplOptions
import org.jetbrains.kotlinx.jupyter.repl.logging.LoggingManager

class FullMagicsHandler(
    replOptions: ReplOptions,
    librariesProcessor: LibrariesProcessor,
    switcher: ResolutionInfoSwitcher<DefaultInfoSwitch>,
    loggingManager: LoggingManager,
) : LogLevelHandlingMagicsHandler(replOptions, librariesProcessor, switcher, loggingManager) {
    override fun handleLogHandler() {
        val logbackLoggingManager = loggingManager as? LogbackLoggingManager ?: return
        val commandArgs = arg?.split(Regex("""\s+""")).orEmpty()
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
            else -> throw ReplException("")
        }
    }
}
