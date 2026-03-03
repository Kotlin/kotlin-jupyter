package org.jetbrains.kotlinx.jupyter.magics

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.choice
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.magics.contexts.CommandHandlingMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.LoggingMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.MagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.requireContext
import org.slf4j.event.Level

/**
 * Handles the %logLevel command for setting the logging level.
 */
class LogLevelMagicsHandler(
    context: MagicHandlerContext,
) : BasicMagicsHandler(context) {
    private val loggingContext = context.requireContext<LoggingMagicHandlerContext>()

    override val callbackMap: Map<ReplLineMagic, () -> Unit> =
        mapOf(
            ReplLineMagic.LOG_LEVEL to ::handleLogLevel,
        )

    /**
     * Handles the %logLevel command, which sets the logging level.
     */
    private fun handleLogLevel() {
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
                            loggingContext.loggingManager.disableLogging()
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
                    loggingContext.loggingManager.setRootLoggingLevel(slf4jLogLevel)
                }
            }
        }.parse(commandHandlingContext.argumentsList())
    }

    companion object : MagicHandlerFactoryImpl(
        ::LogLevelMagicsHandler,
        listOf(
            LoggingMagicHandlerContext::class,
            CommandHandlingMagicHandlerContext::class,
        ),
    )
}
