package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryReference
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionInfo
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable
import java.io.File
import java.net.URL

/**
 * Split a command argument into a list of library calls
 */
fun splitLibraryCalls(text: String): List<String> {
    return libraryCommaRanges(text)
        .mapNotNull { (from, to) ->
            if (from >= to) null
            else text.substring(from + 1, to).trim().takeIf { it.isNotEmpty() }
        }
}

fun libraryCommaRanges(text: String): List<Pair<Int, Int>> {
    return libraryCommaIndices(text, withFirst = true, withLast = true).zipWithNext()
}

/**
 * Need special processing of ',' to skip call argument delimiters in brackets
 * E.g. "use lib1(3), lib2(2, 5)" should split into "lib1(3)" and "lib(2, 5)", not into "lib1(3)", "lib(2", "5)"
 */
fun libraryCommaIndices(text: String, withFirst: Boolean = false, withLast: Boolean = false): List<Int> {
    return buildList {
        var i = 0
        var commaDepth = 0
        val delimiters = charArrayOf(',', '(', ')')
        if (withFirst) add(-1)

        while (true) {
            i = text.indexOfAny(delimiters, i)
            if (i == -1) {
                if (withLast) add(text.length)
                break
            }
            when (text[i]) {
                ',' -> if (commaDepth == 0) {
                    add(i)
                }
                '(' -> commaDepth++
                ')' -> commaDepth--
            }
            i++
        }
    }
}

fun parseReferenceWithArgs(str: String): Pair<LibraryReference, List<Variable>> {
    val (fullName, vars) = parseLibraryName(str)
    val reference = parseReference(fullName)
    return reference to vars
}

private fun parseResolutionInfo(string: String): LibraryResolutionInfo {
    // In case of empty string after `@`: %use lib@
    if (string.isBlank()) return AbstractLibraryResolutionInfo.Default()

    val (type, vars) = parseCall(string, Brackets.SQUARE)
    return defaultParsers[type]?.getInfo(vars) ?: AbstractLibraryResolutionInfo.Default(type)
}

private fun parseReference(string: String): LibraryReference {
    val sepIndex = string.indexOf('@')
    if (sepIndex == -1) return LibraryReference(AbstractLibraryResolutionInfo.Default(), string)

    val nameString = string.substring(0, sepIndex)
    val infoString = string.substring(sepIndex + 1)
    val info = parseResolutionInfo(infoString)
    return LibraryReference(info, nameString)
}

private val defaultParsers = listOf(
    LibraryResolutionInfoParser.make("ref", listOf(Parameter.Required("ref"))) { args ->
        AbstractLibraryResolutionInfo.getInfoByRef(args["ref"] ?: error("Argument 'ref' should be specified"))
    },
    LibraryResolutionInfoParser.make("file", listOf(Parameter.Required("path"))) { args ->
        AbstractLibraryResolutionInfo.ByFile(File(args["path"] ?: error("Argument 'path' should be specified")))
    },
    LibraryResolutionInfoParser.make("dir", listOf(Parameter.Required("dir"))) { args ->
        AbstractLibraryResolutionInfo.ByDir(File(args["dir"] ?: error("Argument 'dir' should be specified")))
    },
    LibraryResolutionInfoParser.make("url", listOf(Parameter.Required("url"))) { args ->
        AbstractLibraryResolutionInfo.ByURL(URL(args["url"] ?: error("Argument 'url' should be specified")))
    },
).associateBy { it.name }
