package org.jetbrains.kotlin.jupyter.magics

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import org.jetbrains.kotlin.jupyter.ExecutedCodeLogging
import org.jetbrains.kotlin.jupyter.OutputConfig
import org.jetbrains.kotlin.jupyter.ReplOptions
import org.jetbrains.kotlin.jupyter.libraries.LibrariesProcessor

class FullMagicsHandler(
    private val repl: ReplOptions,
    libraries: LibrariesProcessor
) : LibrariesOnlyMagicsHandler(libraries, repl.librariesDir, repl.currentBranch) {

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

        return if (parser.reset) OutputConfig() else
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
}
