package org.jetbrains.kotlinx.jupyter.magics

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.FileAppender
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import org.jetbrains.kotlinx.jupyter.LoggingManagement.addAppender
import org.jetbrains.kotlinx.jupyter.LoggingManagement.allLogAppenders
import org.jetbrains.kotlinx.jupyter.LoggingManagement.removeAppender
import org.jetbrains.kotlinx.jupyter.LoggingManagement.setRootLoggingLevel
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.libraries.DefaultInfoSwitch
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.repl.ExecutedCodeLogging
import org.jetbrains.kotlinx.jupyter.repl.OutputConfig
import org.jetbrains.kotlinx.jupyter.repl.ReplOptions

class FullMagicsHandler(
    private val repl: ReplOptions,
    librariesProcessor: LibrariesProcessor,
    switcher: ResolutionInfoSwitcher<DefaultInfoSwitch>,
) : UseMagicsHandler(
    librariesProcessor,
    switcher,
) {

    private fun updateOutputConfig(conf: OutputConfig, argv: List<String>): OutputConfig {
        val parser = object : CliktCommand() {
            val max: Int by option("--max-cell-size", help = "Maximum cell output").int().default(conf.cellOutputMaxSize)
            val maxBuffer: Int by option("--max-buffer", help = "Maximum buffer size").int().default(conf.captureBufferMaxSize)
            val maxBufferNewline: Int by option("--max-buffer-newline", help = "Maximum buffer size when newline got").int().default(conf.captureNewlineBufferSize)
            val maxTimeInterval: Long by option("--max-time", help = "Maximum time wait for output to accumulate").long().default(conf.captureBufferTimeLimitMs)
            val dontCaptureStdout: Boolean by option("--no-stdout", help = "Don't capture output").flag(default = !conf.captureOutput)
            val reset: Boolean by option("--reset-to-defaults", help = "Reset to defaults").flag()
            override fun run() {}
        }
        parser.parse(argv)

        return if (parser.reset) OutputConfig() else {
            with(parser) {
                OutputConfig(
                    !dontCaptureStdout,
                    maxTimeInterval,
                    maxBuffer,
                    max,
                    maxBufferNewline,
                )
            }
        }
    }

    override fun handleTrackExecution() {
        object : CliktCommand() {
            val logLevel by argument().enum<ExecutedCodeLogging>(true) { it.name.lowercase() }.optional()
            override fun run() {
                repl.executedCodeLogging = logLevel ?: ExecutedCodeLogging.ALL
            }
        }.parse(argumentsList())
    }

    override fun handleTrackClasspath() {
        handleSingleOptionalFlag {
            repl.trackClasspath = it ?: true
        }
    }

    override fun handleDumpClassesForSpark() {
        handleSingleOptionalFlag {
            repl.writeCompiledClasses = it ?: true
        }
    }

    override fun handleOutput() {
        repl.outputConfig = updateOutputConfig(repl.outputConfig, argumentsList())
    }

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
                setRootLoggingLevel(level)
            }
        }.parse(argumentsList())
    }

    override fun handleLogHandler() {
        val commandArgs = arg?.split(Regex("""\s+""")).orEmpty()
        val command = commandArgs.firstOrNull() ?: throw ReplException("Log handler command has not been passed")
        when (command) {
            "list" -> {
                println("Log appenders:")
                allLogAppenders().forEach {
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

                val appender: Appender<ILoggingEvent> = when (appenderType) {
                    "--file" -> {
                        val fileName = appenderTypeArgs.getOrNull(0) ?: throw ReplException("File appender needs file name to be specified")
                        val res = FileAppender<ILoggingEvent>()
                        res.file = fileName
                        res
                    }
                    else -> throw ReplException("Unknown appender type: $appenderType")
                }
                addAppender(appenderName, appender)
            }
            "remove" -> {
                val appenderName = commandArgs.getOrNull(1) ?: throw ReplException("Log handler remove command needs appender name argument")
                removeAppender(appenderName)
            }
            else -> throw ReplException("")
        }
    }
}
