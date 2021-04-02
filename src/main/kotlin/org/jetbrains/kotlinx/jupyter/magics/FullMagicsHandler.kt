package org.jetbrains.kotlinx.jupyter.magics

import ch.qos.logback.classic.Level
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import org.jetbrains.kotlinx.jupyter.ExecutedCodeLogging
import org.jetbrains.kotlinx.jupyter.OutputConfig
import org.jetbrains.kotlinx.jupyter.ReplOptions
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.libraries.DefaultInfoSwitch
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.setLevelForAllLoggers

class FullMagicsHandler(
    private val repl: ReplOptions,
    librariesProcessor: LibrariesProcessor,
    switcher: ResolutionInfoSwitcher<DefaultInfoSwitch>,
) : SharedMagicsHandler(
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
                    maxBufferNewline
                )
            }
        }
    }

    override fun handleTrackExecution() {
        repl.executedCodeLogging = when (arg?.trim()) {
            "-all" -> ExecutedCodeLogging.All
            "-off" -> ExecutedCodeLogging.Off
            "-generated" -> ExecutedCodeLogging.Generated
            else -> ExecutedCodeLogging.All
        }
    }

    override fun handleTrackClasspath() {
        repl.trackClasspath = true
    }

    override fun handleDumpClassesForSpark() {
        repl.writeCompiledClasses = true
    }

    override fun handleOutput() {
        repl.outputConfig = updateOutputConfig(repl.outputConfig, (arg ?: "").split(" "))
    }

    override fun handleLogLevel() {
        val level = when (val levelStr = arg?.trim()) {
            "off" -> Level.OFF
            "error" -> Level.ERROR
            "warn" -> Level.WARN
            "info" -> Level.INFO
            "debug" -> Level.DEBUG
            else -> throw ReplException("Unknown log level: '$levelStr'")
        }
        setLevelForAllLoggers(level)
    }
}
