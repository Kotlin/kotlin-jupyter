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
import org.jetbrains.kotlinx.jupyter.libraries.DefaultInfoSwitch
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.repl.ExecutedCodeLogging
import org.jetbrains.kotlinx.jupyter.repl.OutputConfig
import org.jetbrains.kotlinx.jupyter.repl.ReplOptions

open class IdeCompatibleMagicsHandler(
    protected val replOptions: ReplOptions,
    librariesProcessor: LibrariesProcessor,
    switcher: ResolutionInfoSwitcher<DefaultInfoSwitch>,
) : UseMagicsHandler(
        librariesProcessor,
        switcher,
    ) {
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

    override fun handleTrackExecution() {
        object : CliktCommand() {
            val logLevel by argument().enum<ExecutedCodeLogging>(true) { it.name.lowercase() }.optional()

            override fun run() {
                replOptions.executedCodeLogging = logLevel ?: ExecutedCodeLogging.ALL
            }
        }.parse(argumentsList())
    }

    override fun handleTrackClasspath() {
        handleSingleOptionalFlag {
            replOptions.trackClasspath = it ?: true
        }
    }

    override fun handleDumpClassesForSpark() {
        handleSingleOptionalFlag {
            replOptions.writeCompiledClasses = it ?: true
        }
    }

    override fun handleOutput() {
        replOptions.outputConfig = updateOutputConfig(replOptions.outputConfig, argumentsList())
    }
}
