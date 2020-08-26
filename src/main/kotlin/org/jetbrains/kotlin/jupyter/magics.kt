package org.jetbrains.kotlin.jupyter

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import org.jetbrains.kotlin.jupyter.common.ReplLineMagics
import org.jetbrains.kotlin.jupyter.libraries.DefaultInfoSwitch
import org.jetbrains.kotlin.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlin.jupyter.libraries.LibraryFactoryDefaultInfoSwitcher

data class MagicProcessingResult(val code: String, val libraries: List<LibraryDefinition>)

class MagicsProcessor(val repl: ReplOptions, private val libraries: LibrariesProcessor) {

    private val libraryResolutionInfoSwitcher = LibraryFactoryDefaultInfoSwitcher.default(libraries.libraryFactory.resolutionInfoProvider, repl.librariesDir, repl.currentBranch)

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

    fun processMagics(code: String, parseOnly: Boolean = false, tryIgnoreErrors: Boolean = false): MagicProcessingResult {

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

                val magic = if (parseOnly) null else ReplLineMagics.valueOfOrNull(keyword)
                if (magic == null && !parseOnly && !tryIgnoreErrors) {
                    throw ReplCompilerException("Unknown line magic keyword: '$keyword'")
                }

                sb.append(code.substring(nextCopyIndex, magicStart))

                when (magic) {
                    ReplLineMagics.trackExecution -> {
                        repl.executedCodeLogging = when (arg?.trim()) {
                            "-all" -> ExecutedCodeLogging.All
                            "-off" -> ExecutedCodeLogging.Off
                            "-generated" -> ExecutedCodeLogging.Generated
                            else -> ExecutedCodeLogging.All
                        }
                    }
                    ReplLineMagics.trackClasspath -> repl.trackClasspath = true
                    ReplLineMagics.dumpClassesForSpark -> repl.writeCompiledClasses = true

                    ReplLineMagics.use -> {
                        try {
                            if (arg == null) throw ReplCompilerException("Need some arguments for 'use' command")
                            newLibraries.addAll(libraries.processNewLibraries(arg))
                        } catch (e: Exception) {
                            if (!tryIgnoreErrors) throw e
                        }
                    }
                    ReplLineMagics.useLatestDescriptors -> {
                        libraryResolutionInfoSwitcher.switch = when (arg?.trim()) {
                            "-on" -> DefaultInfoSwitch.GIT_REFERENCE
                            "-off" -> DefaultInfoSwitch.DIRECTORY
                            else -> DefaultInfoSwitch.GIT_REFERENCE
                        }
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
