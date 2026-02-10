package org.jetbrains.kotlinx.jupyter.magics

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.magics.contexts.CommandHandlingMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.MagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.ReplOptionsMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.requireContext
import org.jetbrains.kotlinx.jupyter.repl.ExecutedCodeLogging
import org.jetbrains.kotlinx.jupyter.repl.OutputConfig

/**
 * Handler for REPL configuration magic commands.
 * Handles commands related to tracking execution, classpath, output configuration, etc.
 */
class ReplOptionsMagicsHandler(
    context: MagicHandlerContext,
) : BasicMagicsHandler(context) {
    private val replOptionsContext = context.requireContext<ReplOptionsMagicHandlerContext>()

    override val callbackMap: Map<ReplLineMagic, () -> Unit> =
        mapOf(
            ReplLineMagic.TRACK_EXECUTION to ::handleTrackExecution,
            ReplLineMagic.TRACK_CLASSPATH to ::handleTrackClasspath,
            ReplLineMagic.DUMP_CLASSES_FOR_SPARK to ::handleDumpClassesForSpark,
            ReplLineMagic.OUTPUT to ::handleOutput,
        )

    /**
     * Handles the %trackExecution command, which sets the executed code logging level.
     */
    private fun handleTrackExecution() {
        object : CliktCommand() {
            val logLevel by argument().enum<ExecutedCodeLogging>(true) { it.name.lowercase() }.optional()

            override fun run() {
                replOptionsContext.replOptions.executedCodeLogging = logLevel ?: ExecutedCodeLogging.ALL
            }
        }.parse(commandHandlingContext.argumentsList())
    }

    /**
     * Handles the %trackClasspath command, which toggles classpath tracking.
     */
    private fun handleTrackClasspath() {
        handleSingleOptionalFlag {
            replOptionsContext.replOptions.trackClasspath = it ?: true
        }
    }

    /**
     * Handles the %dumpClassesForSpark command, which toggles writing compiled classes for Spark.
     */
    private fun handleDumpClassesForSpark() {
        handleSingleOptionalFlag {
            replOptionsContext.replOptions.writeCompiledClasses = it ?: true
        }
    }

    /**
     * Handles the %output command, which configures output settings.
     */
    private fun handleOutput() {
        replOptionsContext.replOptions.outputConfig =
            updateOutputConfig(
                replOptionsContext.replOptions.outputConfig,
                commandHandlingContext.argumentsList(),
            )
    }

    /**
     * Updates the output configuration based on command-line arguments.
     */
    private fun updateOutputConfig(
        conf: OutputConfig,
        argv: List<String>,
    ): OutputConfig {
        val parser =
            object : CliktCommand() {
                val max: Int by option("--max-cell-size", help = "Maximum cell output").int().default(conf.cellOutputMaxSize)
                val maxBuffer: Int by option("--max-buffer", help = "Maximum buffer size").int().default(conf.captureBufferMaxSize)
                val maxBufferNewline: Int by option(
                    "--max-buffer-newline",
                    help = "Maximum buffer size when newline got",
                ).int().default(conf.captureNewlineBufferSize)
                val maxTimeInterval: Long by option(
                    "--max-time",
                    help = "Maximum time wait for output to accumulate",
                ).long().default(conf.captureBufferTimeLimitMs)
                val dontCaptureStdout: Boolean by option("--no-stdout", help = "Don't capture output").flag(default = !conf.captureOutput)
                val reset: Boolean by option("--reset-to-defaults", help = "Reset to defaults").flag()

                override fun run() {}
            }
        parser.parse(argv)

        return if (parser.reset) {
            OutputConfig()
        } else {
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

    companion object : MagicHandlerFactoryImpl(
        ::ReplOptionsMagicsHandler,
        listOf(
            ReplOptionsMagicHandlerContext::class,
            CommandHandlingMagicHandlerContext::class,
        ),
    )
}
