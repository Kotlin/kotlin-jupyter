package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.textResult
import org.jetbrains.kotlin.jupyter.repl.completion.CompletionResult
import org.jetbrains.kotlin.jupyter.repl.completion.KotlinCompleter
import org.jetbrains.kotlin.jupyter.repl.completion.ListErrorsResult
import org.jetbrains.kotlin.jupyter.repl.completion.SourceCodeImpl
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.SourceCodeCompletionVariant
import kotlin.script.experimental.jvm.util.toSourceCodePosition

enum class ReplCommands(val desc: String) {
    help("display help"),
    classpath("show current classpath")
}

fun isCommand(code: String): Boolean = code.startsWith(":")

fun getCommand(string: String): ReplCommands? {
    return try {
        ReplCommands.valueOf(string)
    } catch (e: IllegalArgumentException) {
        null
    }
}

fun <T> Iterable<T>.joinToStringIndented(transform: ((T) -> CharSequence)? = null) = joinToString("\n    ", prefix = "    ", transform = transform)

fun reportCommandErrors(code: String): ListErrorsResult {
    val commandString = code.trim().substring(1)
    val command = getCommand(commandString)
    if (command != null) return ListErrorsResult(code)

    val sourceCode = SourceCodeImpl(0, code)
    val location = SourceCode.Location(
            0.toSourceCodePosition(sourceCode),
            (commandString.length + 1).toSourceCodePosition(sourceCode)
    )
    return ListErrorsResult(code, sequenceOf(
            ScriptDiagnostic(ScriptDiagnostic.unspecifiedError, "Unknown command", location = location)
    ))
}

fun doCommandCompletion(code: String, cursor: Int): CompletionResult {
    val prefix = code.substring(1, cursor)
    val suitableCommands = ReplCommands.values().filter { it.name.startsWith(prefix) }
    val completions = suitableCommands.map {
        SourceCodeCompletionVariant(it.name, it.name, "command", "command")
    }
    return KotlinCompleter.getResult(code, cursor, completions)
}

fun runCommand(code: String, repl: ReplForJupyter?): Response {
    val args = code.trim().substring(1).split(" ")
    val cmd = getCommand(args[0]) ?: return AbortResponseWithMessage(textResult("Failed!"), "unknown command: $code\nto see available commands, enter :help")
    return when (cmd) {
        ReplCommands.classpath -> {
            val cp = repl!!.currentClasspath
            OkResponseWithMessage(textResult("Current classpath (${cp.count()} paths):\n${cp.joinToString("\n")}"))
        }
        ReplCommands.help -> {
            val commands = ReplCommands.values().asIterable().joinToStringIndented { ":${it.name} - ${it.desc}" }
            val magics = ReplLineMagics.values().asIterable().filter { it.visibleInHelp }.joinToStringIndented {
                var s = "%${it.name} - ${it.desc}"
                if (it.argumentsUsage != null) s += "\n        Usage: %${it.name} ${it.argumentsUsage}"
                s
            }
            val libraries = repl?.resolverConfig?.libraries?.awaitBlocking()?.toList()?.joinToStringIndented {
                "${it.first} ${it.second.link ?: ""}"
            }
            OkResponseWithMessage(textResult("Commands:\n$commands\n\nMagics\n$magics\n\nSupported libraries:\n$libraries"))
        }
    }
}
