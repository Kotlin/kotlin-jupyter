package org.jetbrains.kotlinx.jupyter.magics

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.FileAppender
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.choice
import org.jetbrains.kotlinx.jupyter.LoggingManager
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.libraries.DefaultInfoSwitch
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.repl.ReplOptions

class FullMagicsHandler(
    replOptions: ReplOptions,
    librariesProcessor: LibrariesProcessor,
    switcher: ResolutionInfoSwitcher<DefaultInfoSwitch>,
    private val loggingManager: LoggingManager,
) : IdeCompatibleMagicsHandler(replOptions, librariesProcessor, switcher) {
    override fun handleLogLevel() {
        object : CliktCommand() {
            val level by argument().choice(
                mapOf(
                    "off" to Level.OFF,
                    "error" to Level.ERROR,
                    "warn" to Level.WARN,
                    "info" to Level.INFO,
                    "debug" to Level.DEBUG,
                ),
                ignoreCase = false,
            )

            override fun run() {
                loggingManager.setRootLoggingLevel(level)
            }
        }.parse(argumentsList())
    }

    override fun handleLogHandler() {
        val commandArgs = arg?.split(Regex("""\s+""")).orEmpty()
        val command = commandArgs.firstOrNull() ?: throw ReplException("Log handler command has not been passed")
        when (command) {
            "list" -> {
                println("Log appenders:")
                loggingManager.allLogAppenders().forEach {
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
                loggingManager.addAppender(appenderName, appender)
            }
            "remove" -> {
                val appenderName =
                    commandArgs.getOrNull(
                        1,
                    ) ?: throw ReplException("Log handler remove command needs appender name argument")
                loggingManager.removeAppender(appenderName)
            }
            else -> throw ReplException("")
        }
    }
}
