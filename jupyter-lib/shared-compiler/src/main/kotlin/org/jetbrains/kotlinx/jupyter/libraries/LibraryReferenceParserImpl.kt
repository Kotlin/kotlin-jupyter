package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryReference
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionInfo
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable
import java.io.File
import java.net.URL

class LibraryReferenceParserImpl(
    private val libraryInfoCache: LibraryInfoCache,
) : LibraryReferenceParser {
    override fun parseReferenceWithArgs(string: String): Pair<LibraryReference, List<Variable>> {
        val (fullName, vars) = parseLibraryName(string)
        val reference = parseLibraryReference(fullName)
        val processedVars = vars.filter { it.name.isNotBlank() || it.value.isNotBlank() }
        return reference to processedVars
    }

    override fun parseLibraryReference(string: String): LibraryReference {
        val sepIndex = string.indexOf('@')
        if (sepIndex == -1) return LibraryReference(AbstractLibraryResolutionInfo.Default(), string)

        val nameString = string.take(sepIndex)
        val infoString = string.drop(sepIndex + 1)
        val info = parseResolutionInfo(infoString)
        return LibraryReference(info, nameString)
    }

    private fun parseResolutionInfo(string: String): LibraryResolutionInfo {
        // In case of empty string after `@`: %use lib@
        if (string.isBlank()) return AbstractLibraryResolutionInfo.Default()

        val (type, vars) = parseCall(string, Brackets.SQUARE)
        return defaultParsers[type]?.getInfo(vars) ?: AbstractLibraryResolutionInfo.Default(type)
    }

    private val defaultParsers =
        listOf(
            LibraryResolutionInfoParser.make("ref", listOf(Parameter.Required("ref"))) { args ->
                libraryInfoCache.getLibraryInfoByRef(args["ref"] ?: error("Argument 'ref' should be specified"))
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
            LibraryResolutionInfoParser.make("classpath", listOf()) {
                AbstractLibraryResolutionInfo.ByClasspath
            },
        ).associateBy { it.name }
}
