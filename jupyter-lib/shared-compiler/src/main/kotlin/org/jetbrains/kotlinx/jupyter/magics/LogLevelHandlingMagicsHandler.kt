package org.jetbrains.kotlinx.jupyter.magics

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.choice
import org.jetbrains.kotlinx.jupyter.libraries.DefaultInfoSwitch
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.repl.ReplOptions
import org.jetbrains.kotlinx.jupyter.repl.logging.LoggingManager
import org.slf4j.event.Level

open class LogLevelHandlingMagicsHandler(
    replOptions: ReplOptions,
    librariesProcessor: LibrariesProcessor,
    switcher: ResolutionInfoSwitcher<DefaultInfoSwitch>,
    protected val loggingManager: LoggingManager,
) : IdeCompatibleMagicsHandler(replOptions, librariesProcessor, switcher) {
    override fun handleLogLevel() {
        object : CliktCommand() {
            val level by argument().choice(
                "off",
                "error",
                "warn",
                "info",
                "debug",
                "trace",
                ignoreCase = false,
            )

            override fun run() {
                val slf4jLogLevel =
                    when (level.lowercase()) {
                        "off" -> {
                            loggingManager.disableLogging()
                            null
                        }
                        "error" -> Level.ERROR
                        "warn" -> Level.WARN
                        "info" -> Level.INFO
                        "debug" -> Level.DEBUG
                        "trace" -> Level.TRACE
                        else -> null
                    }
                if (slf4jLogLevel != null) {
                    loggingManager.setRootLoggingLevel(slf4jLogLevel)
                }
            }
        }.parse(argumentsList())
    }
}
