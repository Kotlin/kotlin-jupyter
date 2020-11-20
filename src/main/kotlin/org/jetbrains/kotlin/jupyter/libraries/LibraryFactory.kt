package org.jetbrains.kotlin.jupyter.libraries

import org.jetbrains.kotlin.jupyter.LibrariesDir
import org.jetbrains.kotlin.jupyter.Variable
import java.io.File
import java.net.URL
import java.nio.file.Paths

class LibraryFactory(
    val resolutionInfoProvider: ResolutionInfoProvider,
    private val parsers: Map<String, LibraryResolutionInfoParser> = defaultParsers,
) {
    fun parseReferenceWithArgs(str: String): Pair<LibraryReference, List<Variable>> {
        val (fullName, vars) = parseLibraryName(str)
        val reference = parseReference(fullName)
        return reference to vars
    }

    fun getStandardResolver(homeDir: String): LibraryResolver {
        // Standard resolver doesn't cache results in memory
        var res: LibraryResolver = FallbackLibraryResolver()
        res = LocalLibraryResolver(res, Paths.get(homeDir, LibrariesDir).toString())
        return res
    }

    private fun parseResolutionInfo(string: String): LibraryResolutionInfo {
        // In case of empty string after `@`: %use lib@
        if (string.isBlank()) return resolutionInfoProvider.get()

        val (type, vars) = parseCall(string, Brackets.SQUARE)
        val parser = parsers[type] ?: return resolutionInfoProvider.get(type)
        return parser.getInfo(vars)
    }

    private fun parseReference(string: String): LibraryReference {
        val sepIndex = string.indexOf('@')
        if (sepIndex == -1) return LibraryReference(resolutionInfoProvider.get(), string)

        val nameString = string.substring(0, sepIndex)
        val infoString = string.substring(sepIndex + 1)
        val info = parseResolutionInfo(infoString)
        return LibraryReference(info, nameString)
    }

    companion object {
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
        ).map { it.name to it }.toMap()

        val EMPTY = LibraryFactory(EmptyResolutionInfoProvider)

        fun withDefaultDirectoryResolution(dir: File) = LibraryFactory(StandardResolutionInfoProvider(LibraryResolutionInfo.ByDir(dir)))
    }
}
