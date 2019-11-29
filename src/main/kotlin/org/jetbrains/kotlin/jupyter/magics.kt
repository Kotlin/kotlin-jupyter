package org.jetbrains.kotlin.jupyter

import org.jetbrains.kotlin.jupyter.repl.spark.ClassWriter

enum class ReplLineMagics(val desc: String, val argumentsUsage: String? = null, val visibleInHelp: Boolean = true) {
    use("include supported libraries", "klaxon(5.0.1), lets-plot"),
    trackClasspath("log current classpath changes"),
    trackCode("log executed code", visibleInHelp = false),
    dumpClassesForSpark("stores compiled repl classes in special folder for Spark integration", visibleInHelp = false)
}

/**
 * Split a command argument into a set of library calls
 * Need special processing of ',' to skip call argument delimeters in brackets
 * E.g. "use lib1(3), lib2(2, 5)" should split into "lib1(3)" and "lib(2, 5)", not into "lib1(3)", "lib(2", "5)"
 */
private fun splitLibraryCalls(text: String): List<String> {
    var i = 0
    var prev = 0
    var commaDepth = 0
    val result = mutableListOf<String>()
    val delim = charArrayOf(',', '(', ')')
    while (true) {
        i = text.indexOfAny(delim, i)
        if (i == -1) {
            val res = text.substring(prev, text.length).trim()
            if (res.isNotEmpty())
                result.add(res)
            return result
        }
        when (text[i]) {
            ',' -> if (commaDepth == 0) {
                val res = text.substring(prev, i).trim()
                if (res.isNotEmpty())
                    result.add(res)
                prev = i + 1
            }
            '(' -> commaDepth++
            ')' -> commaDepth--
        }
        i++
    }
}

fun processMagics(repl: ReplForJupyter, code: String): String {

    val sb = StringBuilder()
    var nextSearchIndex = 0
    var nextCopyIndex = 0

    while (true) {

        var magicStart = -1
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
                ReplLineMagics.trackCode -> repl.trackExecutedCode = true
                ReplLineMagics.trackClasspath -> repl.trackClasspath = true
                ReplLineMagics.dumpClassesForSpark -> {
                    val cw = ClassWriter()
                    System.setProperty("spark.repl.class.outputDir", cw.outputDir.toString())
                    repl.classWriter = cw
                }
                ReplLineMagics.use -> {
                    if (arg == null) throw ReplCompilerException("Need some arguments for 'use' command")
                    splitLibraryCalls(arg).forEach {
                        sb.append(repl.librariesCodeGenerator.generateCodeForLibrary(repl, it))
                    }
                }
            }
            nextCopyIndex = magicEnd
            nextSearchIndex = magicEnd
        } catch (e: Exception) {
            throw ReplCompilerException("Failed to process '%$magicText' command. " + e.message)
        }
    }
}