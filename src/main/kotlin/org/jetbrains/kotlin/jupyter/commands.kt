package org.jetbrains.kotlin.jupyter

import org.jetbrains.kotlin.jupyter.api.textResult
import org.jetbrains.kotlin.jupyter.common.ReplCommands
import org.jetbrains.kotlin.jupyter.common.ReplLineMagics
import org.jetbrains.kotlin.jupyter.libraries.parseLibraryDescriptor
import org.jetbrains.kotlin.jupyter.repl.CompletionResult
import org.jetbrains.kotlin.jupyter.repl.KotlinCompleter
import org.jetbrains.kotlin.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlin.jupyter.repl.SourceCodeImpl
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.SourceCodeCompletionVariant
import kotlin.script.experimental.jvm.util.toSourceCodePosition

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
    return ListErrorsResult(
        code,
        sequenceOf(
            ScriptDiagnostic(ScriptDiagnostic.unspecifiedError, "Unknown command", location = location)
        )
    )
}

fun doCommandCompletion(code: String, cursor: Int): CompletionResult {
    val prefix = code.substring(1, cursor)
    val suitableCommands = ReplCommands.values().filter { it.name.startsWith(prefix) }
    val completions = suitableCommands.map {
        SourceCodeCompletionVariant(it.name, it.name, "command", "command")
    }
    return KotlinCompleter.getResult(code, cursor, completions)
}

fun runCommand(code: String, repl: ReplForJupyter): Response {
    val args = code.trim().substring(1).split(" ")
    val cmd = getCommand(args[0]) ?: return AbortResponseWithMessage("Unknown command: $code\nTo see available commands, enter :help")
    return when (cmd) {
        ReplCommands.classpath -> {
            val cp = repl.currentClasspath
            OkResponseWithMessage(textResult("Current classpath (${cp.count()} paths):\n${cp.joinToString("\n")}"))
        }
        ReplCommands.help -> {
            val commands = ReplCommands.values().asIterable().joinToStringIndented { ":${it.name} - ${it.desc}" }
            val magics = ReplLineMagics.values().asIterable().filter { it.visibleInHelp }.joinToStringIndented {
                var s = "%${it.name} - ${it.desc}"
                if (it.argumentsUsage != null) s += "\n        Usage: %${it.name} ${it.argumentsUsage}"
                s
            }
            val libraryFiles =
                repl.homeDir?.resolve(LibrariesDir)?.listFiles { file -> file.isFile && file.name.endsWith(".$LibraryDescriptorExt") } ?: emptyArray()
            val libraries = libraryFiles.toList().mapNotNull { file ->
                val libraryName = file.nameWithoutExtension
                log.info("Parsing descriptor for library '$libraryName'")
                val descriptor = log.catchAll("Parsing descriptor for library '$libraryName' failed") {
                    parseLibraryDescriptor(file.readText())
                }

                if (descriptor != null) {
                    val link = if (descriptor.link != null) " (${descriptor.link})" else ""
                    val description = if (descriptor.description != null) " - ${descriptor.description}" else ""
                    "$libraryName$link$description"
                } else {
                    null
                }
            }.joinToStringIndented()
            OkResponseWithMessage(textResult("Commands:\n$commands\n\nMagics\n$magics\n\nSupported libraries:\n$libraries"))
        }
    }
}
