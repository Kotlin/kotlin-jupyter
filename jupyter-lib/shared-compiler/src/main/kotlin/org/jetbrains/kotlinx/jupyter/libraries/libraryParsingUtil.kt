package org.jetbrains.kotlinx.jupyter.libraries

import java.io.File
import java.net.URL

fun parseReferenceWithArgs(str: String): Pair<LibraryReference, List<Variable>> {
    val (fullName, vars) = parseLibraryName(str)
    val reference = parseReference(fullName)
    return reference to vars
}

private fun parseResolutionInfo(string: String): LibraryResolutionInfo {
    // In case of empty string after `@`: %use lib@
    if (string.isBlank()) return LibraryResolutionInfo.Default()

    val (type, vars) = parseCall(string, Brackets.SQUARE)
    return defaultParsers[type]?.getInfo(vars) ?: LibraryResolutionInfo.Default(type)
}

private fun parseReference(string: String): LibraryReference {
    val sepIndex = string.indexOf('@')
    if (sepIndex == -1) return LibraryReference(LibraryResolutionInfo.Default(), string)

    val nameString = string.substring(0, sepIndex)
    val infoString = string.substring(sepIndex + 1)
    val info = parseResolutionInfo(infoString)
    return LibraryReference(info, nameString)
}

private val defaultParsers = listOf(
    LibraryResolutionInfoParser.make("ref", listOf(Parameter.Required("ref"))) { args ->
        LibraryResolutionInfo.getInfoByRef(args["ref"] ?: error("Argument 'ref' should be specified"))
    },
    LibraryResolutionInfoParser.make("file", listOf(Parameter.Required("path"))) { args ->
        LibraryResolutionInfo.ByFile(File(args["path"] ?: error("Argument 'path' should be specified")))
    },
    LibraryResolutionInfoParser.make("dir", listOf(Parameter.Required("dir"))) { args ->
        LibraryResolutionInfo.ByDir(File(args["dir"] ?: error("Argument 'dir' should be specified")))
    },
    LibraryResolutionInfoParser.make("url", listOf(Parameter.Required("url"))) { args ->
        LibraryResolutionInfo.ByURL(URL(args["url"] ?: error("Argument 'url' should be specified")))
    },
).associateBy { it.name }
