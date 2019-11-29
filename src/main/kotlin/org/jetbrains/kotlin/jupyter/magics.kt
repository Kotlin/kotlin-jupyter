package org.jetbrains.kotlin.jupyter

import org.jetbrains.kotlin.jupyter.repl.spark.ClassWriter

enum class ReplLineMagics(val desc: String, val argumentsUsage: String? = null, val visibleInHelp: Boolean = true) {
    use("include supported libraries", "klaxon(5.0.1), lets-plot"),
    trackClasspath("log current classpath changes"),
    trackExecution("log code that is going to be executed in repl", visibleInHelp = false),
    dumpClassesForSpark("stores compiled repl classes in special folder for Spark integration", visibleInHelp = false)
}

fun processMagics(repl: ReplForJupyter, code: String): String {

    val sb = StringBuilder()
    var nextSearchIndex = 0
    var nextCopyIndex = 0

    while (true) {

        var magicStart: Int
        do {
            magicStart = code.indexOf("%", nextSearchIndex)
            nextSearchIndex = magicStart + 1
        } while (magicStart != -1 && magicStart != 0 && code[magicStart - 1] != '\n')
        if (magicStart == -1) {
            sb.append(code.substring(nextCopyIndex))
            return sb.toString()
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
                ReplLineMagics.dumpClassesForSpark -> {
                    val cw = ClassWriter()
                    System.setProperty("spark.repl.class.outputDir", cw.outputDir.toString())
                    repl.classWriter = cw
                }
                ReplLineMagics.use -> {
                    if (arg == null) throw ReplCompilerException("Need some arguments for 'use' command")
                    repl.librariesCodeGenerator.processNewLibraries(repl, arg)
                }
            }
            nextCopyIndex = magicEnd
            nextSearchIndex = magicEnd
        } catch (e: Exception) {
            throw ReplCompilerException("Failed to process '%$magicText' command. " + e.message)
        }
    }
}