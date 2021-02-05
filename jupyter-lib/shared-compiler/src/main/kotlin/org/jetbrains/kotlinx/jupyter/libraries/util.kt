package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer
import org.jetbrains.kotlinx.jupyter.util.replaceVariables
import java.io.File
import java.nio.file.Paths
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic

sealed class Parameter(val name: String, open val default: String?) {
    class Required(name: String) : Parameter(name, null)
    class Optional(name: String, override val default: String) : Parameter(name, default)
}

class Brackets(
    val open: Char,
    @Suppress("MemberVisibilityCanBePrivate") val close: Char
) {
    val argRegex: Regex

    init {
        val endCharsStr = ",\\$close"
        argRegex = Regex("""\s*((?<name>\p{Alnum}+)\s*=\s*)?((?<raw>[^$endCharsStr" \t\r\n]*)|("(?<quoted>((\\.)|[^"\\])*)"))\s*[$endCharsStr]""")
    }

    companion object {
        val ROUND = Brackets('(', ')')
        val SQUARE = Brackets('[', ']')
    }
}

enum class DefaultInfoSwitch {
    GIT_REFERENCE, DIRECTORY
}

class ResolutionInfoSwitcher<T>(private val infoProvider: ResolutionInfoProvider, initialSwitchVal: T, private val switcher: (T) -> LibraryResolutionInfo) {
    private val defaultInfoCache = hashMapOf<T, LibraryResolutionInfo>()

    var switch: T = initialSwitchVal
        set(value) {
            infoProvider.fallback = defaultInfoCache.getOrPut(value) { switcher(value) }
            field = value
        }

    companion object {
        fun default(provider: ResolutionInfoProvider, defaultDir: File, defaultRef: String): ResolutionInfoSwitcher<DefaultInfoSwitch> {
            val initialInfo = provider.fallback

            val dirInfo = if (initialInfo is LibraryResolutionInfo.ByDir) {
                initialInfo
            } else {
                LibraryResolutionInfo.ByDir(defaultDir)
            }

            val refInfo = if (initialInfo is LibraryResolutionInfo.ByGitRef) {
                initialInfo
            } else {
                LibraryResolutionInfo.getInfoByRef(defaultRef)
            }

            return ResolutionInfoSwitcher(provider, DefaultInfoSwitch.DIRECTORY) { switch ->
                when (switch) {
                    DefaultInfoSwitch.DIRECTORY -> dirInfo
                    DefaultInfoSwitch.GIT_REFERENCE -> refInfo
                }
            }
        }

        // Used in Kotlin Jupyter plugin for IDEA
        @Suppress("unused")
        fun noop(provider: ResolutionInfoProvider): ResolutionInfoSwitcher<DefaultInfoSwitch> {
            return ResolutionInfoSwitcher(provider, DefaultInfoSwitch.GIT_REFERENCE) {
                provider.fallback
            }
        }
    }
}

fun diagFailure(message: String): ResultWithDiagnostics.Failure {
    return ResultWithDiagnostics.Failure(ScriptDiagnostic(ScriptDiagnostic.unspecifiedError, message))
}

data class ArgParseResult(
    val variable: Variable,
    val end: Int
)

private val unescapeRegex = Regex("""\\(.)""")
private fun String.unescape() = unescapeRegex.replace(this, "$1")

private fun parseLibraryArgument(str: String, brackets: Brackets, begin: Int): ArgParseResult? {
    if (begin >= str.length) return null

    val match = brackets.argRegex.find(str, begin) ?: return null
    val groups = match.groups

    val name = groups["name"]?.value.orEmpty()

    val raw = groups["raw"]?.value
    val quoted = groups["quoted"]?.value
    val endIndex = match.range.last + 1
    val value = raw ?: quoted?.unescape() ?: ""

    return ArgParseResult(Variable(name, value), endIndex)
}

fun parseCall(str: String, brackets: Brackets): Pair<String, List<Variable>> {
    val openBracketIndex = str.indexOf(brackets.open)
    if (openBracketIndex == -1) return str.trim() to emptyList()
    val name = str.substring(0, openBracketIndex).trim()
    val argsString = str.substring(openBracketIndex + 1)

    val firstArg = parseLibraryArgument(argsString, brackets, 0)
    val args = generateSequence(firstArg) {
        parseLibraryArgument(argsString, brackets, it.end)
    }.map {
        it.variable
    }.toList()

    return name to args
}

fun parseLibraryName(str: String): Pair<String, List<Variable>> {
    return parseCall(str, Brackets.ROUND)
}

fun getStandardResolver(homeDir: String? = null, infoProvider: ResolutionInfoProvider): LibraryResolver {
    // Standard resolver doesn't cache results in memory
    var res: LibraryResolver = FallbackLibraryResolver(infoProvider)
    val librariesDir: String? = homeDir?.let { Paths.get(it, LibrariesDir).toString() }
    res = LocalLibraryResolver(res, librariesDir)
    return res
}

class TrivialLibraryDefinitionProducer(private val library: LibraryDefinition) : LibraryDefinitionProducer {
    override fun getDefinitions(notebook: Notebook<*>?): List<LibraryDefinition> {
        return listOf(library)
    }
}

fun List<LibraryDefinitionProducer>.getDefinitions(notebook: Notebook<*>?): List<LibraryDefinition> {
    return flatMap { it.getDefinitions(notebook) }
}

fun LibraryDefinition.buildDependenciesInitCode(mapping: Map<String, String> = emptyMap()): Code? {
    val builder = StringBuilder()
    repositories.forEach { builder.appendLine("@file:Repository(\"${replaceVariables(it, mapping)}\")") }
    dependencies.forEach { builder.appendLine("@file:DependsOn(\"${replaceVariables(it, mapping)}\")") }
    imports.forEach { builder.appendLine("import ${replaceVariables(it, mapping)}") }
    return if (builder.isNotBlank()) builder.toString() else null
}
