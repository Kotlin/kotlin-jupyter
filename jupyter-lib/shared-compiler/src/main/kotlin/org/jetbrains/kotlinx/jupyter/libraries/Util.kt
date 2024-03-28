package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable
import java.util.TreeSet
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic

sealed class Parameter(val name: String, open val default: String?) {
    class Required(name: String) : Parameter(name, null)
    class Optional(name: String, override val default: String) : Parameter(name, default)
}

class Brackets(
    val open: Char,
    @Suppress("MemberVisibilityCanBePrivate") val close: Char,
) {
    val argRegex: Regex

    init {
        val endCharsStr = ",\\$close"
        argRegex = Regex("""\s*((?<name>(\p{Alnum}|[._-])+)\s*=\s*)?((?<raw>[^$endCharsStr" \t\r\n]*)|("(?<quoted>((\\.)|[^"\\])*)"))\s*[$endCharsStr]""")
    }

    companion object {
        val ROUND = Brackets('(', ')')
        val SQUARE = Brackets('[', ']')
    }
}

enum class DefaultInfoSwitch {
    GIT_REFERENCE, DIRECTORY, CLASSPATH
}

fun diagFailure(message: String): ResultWithDiagnostics.Failure {
    return ResultWithDiagnostics.Failure(ScriptDiagnostic(ScriptDiagnostic.unspecifiedError, message))
}

data class ArgParseResult(
    val variable: Variable,
    val end: Int,
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
    return name to parseLibraryArguments(argsString, brackets).map { it.variable }.toList()
}

fun parseLibraryArguments(argumentsString: String, brackets: Brackets, start: Int = 0): Sequence<ArgParseResult> {
    val firstArg = parseLibraryArgument(argumentsString, brackets, start)
    return generateSequence(firstArg) {
        parseLibraryArgument(argumentsString, brackets, it.end)
    }
}

fun parseLibraryName(str: String): Pair<String, List<Variable>> {
    return parseCall(str, Brackets.ROUND)
}

class TrivialLibraryDefinitionProducer(private val library: LibraryDefinition) : LibraryDefinitionProducer {
    override fun getDefinitions(notebook: Notebook): List<LibraryDefinition> {
        return listOf(library)
    }
}

fun List<LibraryDefinitionProducer>.getDefinitions(notebook: Notebook): List<LibraryDefinition> {
    return flatMap { it.getDefinitions(notebook) }
}

fun String.escapeSpecialChars(): String {
    return buildString {
        for (char in this@escapeSpecialChars) {
            when (char) {
                '\\' -> append("\\\\")
                '\"' -> append("\\\"")
                '\'' -> append("\\'")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}

fun buildDependenciesInitCode(libraries: Collection<LibraryDefinition>): Code? {
    val builder = StringBuilder()
    libraries.flatMapTo(TreeSet()) { it.repositories }.forEach { repo ->
        val pathArg = "\"${repo.path.escapeSpecialChars()}\""
        val usernameArg = repo.username?.let { ", username=\"${it.escapeSpecialChars()}\"" }.orEmpty()
        val passwordArg = repo.password?.let { ", password=\"${it.escapeSpecialChars()}\"" }.orEmpty()
        builder.appendLine("@file:Repository($pathArg$usernameArg$passwordArg)")
    }
    libraries.flatMapTo(TreeSet()) { it.dependencies }.forEach { builder.appendLine("@file:DependsOn(\"$it\")") }
    libraries.flatMapTo(TreeSet()) { it.imports }.forEach { builder.appendLine("import $it") }
    return if (builder.isNotBlank()) builder.toString() else null
}
