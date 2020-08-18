package org.jetbrains.kotlin.jupyter.libraries

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.jetbrains.kotlin.jupyter.GitHubApiPrefix
import org.jetbrains.kotlin.jupyter.LibrariesDir
import org.jetbrains.kotlin.jupyter.LibraryDescriptor
import org.jetbrains.kotlin.jupyter.ReplCompilerException
import org.jetbrains.kotlin.jupyter.TypeHandler
import org.jetbrains.kotlin.jupyter.Variable
import org.jetbrains.kotlin.jupyter.catchAll
import org.jetbrains.kotlin.jupyter.getHttp
import org.jetbrains.kotlin.jupyter.log
import org.json.JSONObject
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic

sealed class Parameter(val name: String, open val default: String?) {
    class Required(name: String): Parameter(name, null)
    class Optional(name: String, override val default: String): Parameter(name, default)
}

class Brackets(val open: Char, val close: Char) {
    companion object {
        val ROUND = Brackets('(', ')')
        val SQUARE = Brackets('[', ']')
    }
}

enum class DefaultInfoSwitch {
    GIT_REFERENCE, DIRECTORY
}

class LibraryFactoryDefaultInfoSwitcher<T>(private val infoProvider: ResolutionInfoProvider, initialSwitchVal: T, private val switcher: (T) -> LibraryResolutionInfo) {
    private val defaultInfoCache = hashMapOf<T, LibraryResolutionInfo>()

    var switch: T = initialSwitchVal
        set(value) {
            infoProvider.fallback = defaultInfoCache.getOrPut(value) { switcher(value) }
            field = value
        }

    companion object {
        fun default(provider: ResolutionInfoProvider, defaultDir: File, defaultRef: String): LibraryFactoryDefaultInfoSwitcher<DefaultInfoSwitch> {
            val initialInfo = provider.fallback
            val dirInfo = if (initialInfo is LibraryResolutionInfo.ByDir) initialInfo else LibraryResolutionInfo.ByDir(defaultDir)
            val refInfo = if (initialInfo is LibraryResolutionInfo.ByGitRef) initialInfo else LibraryResolutionInfo.getInfoByRef(defaultRef)
            return LibraryFactoryDefaultInfoSwitcher(provider, DefaultInfoSwitch.DIRECTORY) { switch ->
                when(switch) {
                    DefaultInfoSwitch.DIRECTORY -> dirInfo
                    DefaultInfoSwitch.GIT_REFERENCE -> refInfo
                }
            }
        }
    }
}

fun diagFailure(message: String): ResultWithDiagnostics.Failure {
    return ResultWithDiagnostics.Failure(ScriptDiagnostic(ScriptDiagnostic.unspecifiedError, message))
}

fun parseLibraryArgument(str: String): Variable {
    val eq = str.indexOf('=')
    return if (eq == -1) Variable("", str.trim())
    else Variable(str.substring(0, eq).trim(), str.substring(eq + 1).trim())
}

fun parseCall(str: String, brackets: Brackets): Pair<String, List<Variable>> {
    val openBracketIndex = str.indexOf(brackets.open)
    if (openBracketIndex == -1) return str.trim() to emptyList()
    val name = str.substring(0, openBracketIndex).trim()
    val args = str.substring(openBracketIndex + 1, str.indexOf(brackets.close, openBracketIndex))
            .split(',')
            .map(::parseLibraryArgument)
    return name to args
}

fun parseLibraryName(str: String): Pair<String, List<Variable>> {
    return parseCall(str, Brackets.ROUND)
}

fun getLatestCommitToLibraries(ref: String, sinceTimestamp: String?): Pair<String, String>? =
        log.catchAll {
            var url = "$GitHubApiPrefix/commits?path=$LibrariesDir&sha=$ref"
            if (sinceTimestamp != null)
                url += "&since=$sinceTimestamp"
            log.info("Checking for new commits to library descriptors at $url")
            val arr = getHttp(url).jsonArray
            if (arr.length() == 0) {
                if (sinceTimestamp != null)
                    getLatestCommitToLibraries(ref, null)
                else {
                    log.info("Didn't find any commits to '$LibrariesDir' at $url")
                    null
                }
            } else {
                val commit = arr[0] as JSONObject
                val sha = commit["sha"] as String
                val timestamp = ((commit["commit"] as JSONObject)["committer"] as JSONObject)["date"] as String
                sha to timestamp
            }
        }

fun parseLibraryDescriptor(json: String): LibraryDescriptor {
    val jsonParser = Parser.default()
    val res = jsonParser.parse(json.byteInputStream())
    if (res is JsonObject) return parseLibraryDescriptor(res)

    throw ReplCompilerException("Result of library descriptor parsing is of type ${res.javaClass.canonicalName} which is unexpected")
}

fun parseLibraryDescriptor(json: JsonObject): LibraryDescriptor {
    return LibraryDescriptor(
            originalJson = json,
            dependencies = json.array<String>("dependencies")?.toList().orEmpty(),
            variables = json.obj("properties")?.map { Variable(it.key, it.value.toString()) }.orEmpty(),
            imports = json.array<String>("imports")?.toList().orEmpty(),
            repositories = json.array<String>("repositories")?.toList().orEmpty(),
            init = json.array<String>("init")?.toList().orEmpty(),
            shutdown = json.array<String>("shutdown")?.toList().orEmpty(),
            initCell = json.array<String>("initCell")?.toList().orEmpty(),
            renderers = json.obj("renderers")?.map { TypeHandler(it.key, it.value.toString()) }?.toList().orEmpty(),
            link = json.string("link"),
            minKernelVersion = json.string("minKernelVersion"),
            converters = json.obj("typeConverters")?.map { TypeHandler(it.key, it.value.toString()) }.orEmpty(),
            annotations = json.obj("annotationHandlers")?.map { TypeHandler(it.key, it.value.toString()) }.orEmpty()
    )
}

fun parseLibraryDescriptors(libJsons: Map<String, JsonObject>): Map<String, LibraryDescriptor> {
    return libJsons.mapValues {
        log.info("Parsing '${it.key}' descriptor")
        parseLibraryDescriptor(it.value)
    }
}
