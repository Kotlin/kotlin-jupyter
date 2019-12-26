package org.jetbrains.kotlin.jupyter

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import org.jetbrains.kotlin.jupyter.repl.spark.ClassWriter

enum class ReplLineMagics(val desc: String, val argumentsUsage: String? = null, val visibleInHelp: Boolean = true) {
    use("include supported libraries", "klaxon(5.0.1), lets-plot"),
    trackClasspath("log current classpath changes"),
    trackExecution("log code that is going to be executed in repl", visibleInHelp = false),
    dumpClassesForSpark("stores compiled repl classes in special folder for Spark integration", visibleInHelp = false),
    output("setup output settings", "--max-cell-size=1000 --no-stdout --max-time=100 --max-buffer=400")
}

data class MagicProcessingResult(val code: String, val libraries: List<LibraryDefinition> = emptyList())

class MagicsProcessor(val repl: ReplOptions, val libraries: LibrariesProcessor) {

    fun updateOutputConfig(conf: OutputConfig, argv: List<String>): OutputConfig {

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

    fun processMagics(code: String): MagicProcessingResult {

        val sb = StringBuilder()
        var nextSearchIndex = 0
        var nextCopyIndex = 0

        val newLibraries = mutableListOf<LibraryDefinition>()
        while (true) {

            var magicStart: Int
            do {
                magicStart = code.indexOf("%", nextSearchIndex)
                nextSearchIndex = magicStart + 1
            } while (magicStart != -1 && magicStart != 0 && code[magicStart - 1] != '\n')
            if (magicStart == -1) {
                sb.append(code.substring(nextCopyIndex))
                return MagicProcessingResult(sb.toString(), newLibraries)
            }

            val magicEnd = code.indexOf('\n', magicStart).let { if (it == -1) code.length else it }
            val magicText = code.substring(magicStart + 1, magicEnd)

            try {
                val parts = magicText.split(' ', limit = 2)
                val keyword = parts[0]
                val arg = if (parts.count() > 1) parts[1] else null

                val magic = try {
                    ReplLineMagics.valueOf(keyword)
                } catch (e: IllegalArgumentException) {
                    throw ReplCompilerException("Unknown line magic keyword: '$keyword'")
                }

                sb.append(code.substring(nextCopyIndex, magicStart))

                when (magic) {
                    ReplLineMagics.trackExecution -> repl.trackExecutedCode = true
                    ReplLineMagics.trackClasspath -> repl.trackClasspath = true
                    ReplLineMagics.dumpClassesForSpark -> repl.writeCompiledClasses = true

                    ReplLineMagics.use -> {
                        if (arg == null) throw ReplCompilerException("Need some arguments for 'use' command")
                        newLibraries.addAll(libraries.processNewLibraries(arg))
                    }
                    ReplLineMagics.output -> {
                        repl.outputConfig = updateOutputConfig(repl.outputConfig, (arg ?: "").split(" "))
                    }
                }
                nextCopyIndex = magicEnd
                nextSearchIndex = magicEnd
            } catch (e: Exception) {
                throw ReplCompilerException("Failed to process '%$magicText' command. " + e.message)
            }
        }
    }
}