package org.jetbrains.kotlin.jupyter

import org.jetbrains.kotlin.jupyter.api.textResult
import org.jetbrains.kotlin.jupyter.common.ReplCommand
import org.jetbrains.kotlin.jupyter.common.ReplLineMagic
import org.jetbrains.kotlin.jupyter.compiler.util.SourceCodeImpl
import org.jetbrains.kotlin.jupyter.config.catchAll
import org.jetbrains.kotlin.jupyter.libraries.LibrariesDir
import org.jetbrains.kotlin.jupyter.libraries.LibraryDescriptorExt
import org.jetbrains.kotlin.jupyter.libraries.parseLibraryDescriptor
import org.jetbrains.kotlin.jupyter.repl.CompletionResult
import org.jetbrains.kotlin.jupyter.repl.KotlinCompleter
import org.jetbrains.kotlin.jupyter.repl.ListErrorsResult
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.SourceCodeCompletionVariant
import kotlin.script.experimental.jvm.util.toSourceCodePosition

fun isCommand(code: String): Boolean = code.startsWith(":")

fun <T> Iterable<T>.joinToStringIndented(transform: ((T) -> CharSequence)? = null) = joinToString("\n    ", prefix = "    ", transform = transform)

fun reportCommandErrors(code: String): ListErrorsResult {
    val commandString = code.trim().substring(1)
    val command = ReplCommand.valueOfOrNull(commandString)
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
    val suitableCommands = ReplCommand.values().filter { it.nameForUser.startsWith(prefix) }
    val completions = suitableCommands.map {
        SourceCodeCompletionVariant(it.nameForUser, it.nameForUser, "command", "command")
    }
    return KotlinCompleter.getResult(code, cursor, completions)
}

fun runCommand(code: String, repl: ReplForJupyter): Response {
    val args = code.trim().substring(1).split(" ")
    val cmd = ReplCommand.valueOfOrNull(args[0]) ?: return AbortResponseWithMessage("Unknown command: $code\nTo see available commands, enter :help")
    return when (cmd) {
        ReplCommand.CLASSPATH -> {
            val cp = repl.currentClasspath
            OkResponseWithMessage(textResult("Current classpath (${cp.count()} paths):\n${cp.joinToString("\n")}"))
        }
        ReplCommand.HELP -> {
            val commands = ReplCommand.values().asIterable().joinToStringIndented { ":${it.nameForUser} - ${it.desc}" }
            val magics = ReplLineMagic.values().asIterable().filter { it.visibleInHelp }.joinToStringIndented {
                var s = "%${it.nameForUser} - ${it.desc}"
                if (it.argumentsUsage != null) s += "\n        Usage: %${it.nameForUser} ${it.argumentsUsage}"
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
